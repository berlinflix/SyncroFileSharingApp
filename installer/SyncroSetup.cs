// Syncro for Windows — setup and uninstaller.
//
// A single self-contained exe: the WPF user interface (Setup.xaml, parsed at runtime so no XAML build step
// is needed), the Roboto typeface and the whole application image (payload.zip) are embedded resources.
// Built with the C# compiler that ships in every Windows 10/11 .NET Framework 4.8 install; see build.ps1.
//
// Per-user install (no administrator rights): %LOCALAPPDATA%\Programs\Syncro, Start menu and optional desktop
// shortcuts, optional start-with-Windows entry, and an entry in Settings > Apps that runs "/uninstall".
//
//   SyncroSetup.exe                 interactive install / update
//   SyncroSetup.exe /S [/D=path]    silent install
//   SyncroSetup.exe /uninstall [/S] remove Syncro

using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Threading;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Markup;
using System.Windows.Media;
using System.Windows.Threading;
using Microsoft.Win32;

namespace Syncro.Setup
{
    public static class Program
    {
        public const string AppName = "Syncro";
        public const string UninstallKey = @"Software\Microsoft\Windows\CurrentVersion\Uninstall\Syncro";
        public const string RunKey = @"Software\Microsoft\Windows\CurrentVersion\Run";

        public static string Version = "2.0.0";

        [STAThread]
        public static int Main(string[] args)
        {
            Version = ReadResourceText("Syncro.version.txt", Version).Trim();
            bool silent = HasArg(args, "/S");
            string customDir = null;
            foreach (string a in args) if (a.StartsWith("/D=", StringComparison.OrdinalIgnoreCase)) customDir = a.Substring(3).Trim('"');

            if (HasArg(args, "/uninstall"))
            {
                string dir = InstalledLocation() ?? Path.GetDirectoryName(SelfPath());
                // The uninstaller lives inside the folder it deletes: run a temporary copy instead.
                if (!HasArg(args, "/fromtemp") && IsInside(SelfPath(), dir))
                {
                    string temp = Path.Combine(Path.GetTempPath(), "SyncroUninstall-" + Guid.NewGuid().ToString("N").Substring(0, 8) + ".exe");
                    File.Copy(SelfPath(), temp, true);
                    Process.Start(new ProcessStartInfo(temp, "/uninstall /fromtemp" + (silent ? " /S" : "")) { UseShellExecute = false });
                    return 0;
                }
                if (silent)
                {
                    try { Installer.Uninstall(dir, false, null); ScheduleSelfDelete(); return 0; }
                    catch { return 1; }
                }
                return RunUi(SetupWindow.Mode.Uninstall, dir);
            }

            if (silent)
            {
                try
                {
                    string dir = customDir ?? InstalledLocation() ?? Installer.DefaultDirectory();
                    Installer.Install(dir, true, true, null);
                    return 0;
                }
                catch { return 1; }
            }
            return RunUi(SetupWindow.Mode.Install, customDir);
        }

        static int RunUi(SetupWindow.Mode mode, string dir)
        {
            var app = new Application();
            app.ShutdownMode = ShutdownMode.OnMainWindowClose;
            var ui = new SetupWindow(mode, dir);
            return app.Run(ui.Window);
        }

        public static bool HasArg(string[] args, string name)
        {
            foreach (string a in args) if (string.Equals(a, name, StringComparison.OrdinalIgnoreCase)) return true;
            return false;
        }

        public static string SelfPath()
        {
            return Process.GetCurrentProcess().MainModule.FileName;
        }

        public static bool IsInside(string path, string dir)
        {
            if (string.IsNullOrEmpty(dir)) return false;
            string full = Path.GetFullPath(path);
            string root = Path.GetFullPath(dir).TrimEnd('\\') + "\\";
            return full.StartsWith(root, StringComparison.OrdinalIgnoreCase);
        }

        public static string InstalledLocation()
        {
            using (RegistryKey key = Registry.CurrentUser.OpenSubKey(UninstallKey))
            {
                if (key == null) return null;
                string location = key.GetValue("InstallLocation") as string;
                return !string.IsNullOrEmpty(location) && Directory.Exists(location) ? location : null;
            }
        }

        public static string InstalledVersion()
        {
            using (RegistryKey key = Registry.CurrentUser.OpenSubKey(UninstallKey))
            {
                return key == null ? null : key.GetValue("DisplayVersion") as string;
            }
        }

        public static Stream Resource(string name)
        {
            return Assembly.GetExecutingAssembly().GetManifestResourceStream(name);
        }

        static string ReadResourceText(string name, string fallback)
        {
            using (Stream s = Resource(name))
            {
                if (s == null) return fallback;
                using (var reader = new StreamReader(s)) return reader.ReadToEnd();
            }
        }

        public static void ScheduleSelfDelete()
        {
            try
            {
                string self = SelfPath();
                if (!self.StartsWith(Path.GetTempPath(), StringComparison.OrdinalIgnoreCase)) return;
                var info = new ProcessStartInfo("cmd.exe", "/c ping 127.0.0.1 -n 3 > nul & del /f /q \"" + self + "\"");
                info.CreateNoWindow = true;
                info.WindowStyle = ProcessWindowStyle.Hidden;
                info.UseShellExecute = false;
                Process.Start(info);
            }
            catch { }
        }
    }

    public delegate void ProgressHandler(double fraction, string step, string detail);

    public static class Installer
    {
        public static string DefaultDirectory()
        {
            return Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Programs", Program.AppName);
        }

        static string StartMenuShortcut()
        {
            return Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.Programs), Program.AppName + ".lnk");
        }

        static string DesktopShortcut()
        {
            return Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory), Program.AppName + ".lnk");
        }

        public static bool HasDesktopShortcut() { return File.Exists(DesktopShortcut()); }

        public static bool StartsWithWindows()
        {
            using (RegistryKey key = Registry.CurrentUser.OpenSubKey(Program.RunKey))
            {
                return key != null && key.GetValue(Program.AppName) != null;
            }
        }

        public static void Install(string dir, bool desktopShortcut, bool startWithWindows, ProgressHandler progress)
        {
            Report(progress, 0.02, "Preparing", dir);
            StopRunning(dir);
            Directory.CreateDirectory(dir);
            // Replace the previous app image on update; leave anything else in the folder alone.
            foreach (string sub in new[] { "app", "runtime" })
            {
                string path = Path.Combine(dir, sub);
                if (Directory.Exists(path)) DeleteDirectory(path);
            }

            long totalBytes = 0;
            long written = 0;
            using (Stream payload = Program.Resource("Syncro.payload.zip"))
            {
                if (payload == null) throw new InvalidOperationException("This setup file is damaged — download it again.");
                using (var zip = new ZipArchive(payload, ZipArchiveMode.Read))
                {
                    foreach (ZipArchiveEntry entry in zip.Entries) totalBytes += entry.Length;
                    string root = Path.GetFullPath(dir).TrimEnd('\\') + "\\";
                    byte[] buffer = new byte[1 << 20];
                    foreach (ZipArchiveEntry entry in zip.Entries)
                    {
                        // Archives made on Windows may use either separator.
                        string name = entry.FullName.Replace('\\', '/');
                        string target = Path.GetFullPath(Path.Combine(dir, name.Replace('/', '\\')));
                        if (!target.StartsWith(root, StringComparison.OrdinalIgnoreCase)) continue;
                        if (name.EndsWith("/") || entry.Name.Length == 0)
                        {
                            Directory.CreateDirectory(target);
                            continue;
                        }
                        Directory.CreateDirectory(Path.GetDirectoryName(target));
                        using (Stream input = entry.Open())
                        using (Stream output = File.Create(target))
                        {
                            int n;
                            while ((n = input.Read(buffer, 0, buffer.Length)) > 0)
                            {
                                output.Write(buffer, 0, n);
                                written += n;
                                if (totalBytes > 0) Report(progress, 0.05 + 0.85 * written / totalBytes, "Copying files", entry.FullName);
                            }
                        }
                    }
                }
            }

            Report(progress, 0.92, "Creating shortcuts", "Start menu");
            string exe = Path.Combine(dir, "Syncro.exe");
            string setupCopy = Path.Combine(dir, "SyncroSetup.exe");
            if (!string.Equals(Path.GetFullPath(Program.SelfPath()), Path.GetFullPath(setupCopy), StringComparison.OrdinalIgnoreCase))
            {
                File.Copy(Program.SelfPath(), setupCopy, true);
            }
            CreateShortcut(StartMenuShortcut(), exe, dir);
            if (desktopShortcut) CreateShortcut(DesktopShortcut(), exe, dir);
            else TryDelete(DesktopShortcut());

            Report(progress, 0.96, "Registering Syncro", "Settings > Apps");
            using (RegistryKey run = Registry.CurrentUser.CreateSubKey(Program.RunKey))
            {
                if (startWithWindows) run.SetValue(Program.AppName, "\"" + exe + "\" --minimized");
                else run.DeleteValue(Program.AppName, false);
            }
            using (RegistryKey key = Registry.CurrentUser.CreateSubKey(Program.UninstallKey))
            {
                key.SetValue("DisplayName", Program.AppName);
                key.SetValue("DisplayVersion", Program.Version);
                key.SetValue("Publisher", "Syncro");
                key.SetValue("DisplayIcon", exe);
                key.SetValue("InstallLocation", dir);
                key.SetValue("UninstallString", "\"" + setupCopy + "\" /uninstall");
                key.SetValue("QuietUninstallString", "\"" + setupCopy + "\" /uninstall /S");
                key.SetValue("URLInfoAbout", "https://github.com/berlinflix/SyncroFileSharingApp");
                key.SetValue("EstimatedSize", (int)(DirectorySize(dir) / 1024), RegistryValueKind.DWord);
                key.SetValue("NoModify", 1, RegistryValueKind.DWord);
                key.SetValue("NoRepair", 1, RegistryValueKind.DWord);
            }
            Report(progress, 1.0, "Done", "");
        }

        public static void Uninstall(string dir, bool removeData, ProgressHandler progress)
        {
            Report(progress, 0.05, "Closing Syncro", "");
            if (!string.IsNullOrEmpty(dir)) StopRunning(dir);
            Report(progress, 0.2, "Removing shortcuts", "");
            TryDelete(StartMenuShortcut());
            TryDelete(DesktopShortcut());
            using (RegistryKey run = Registry.CurrentUser.OpenSubKey(Program.RunKey, true))
            {
                if (run != null) run.DeleteValue(Program.AppName, false);
            }
            Report(progress, 0.35, "Removing files", dir);
            if (!string.IsNullOrEmpty(dir) && Directory.Exists(dir) && File.Exists(Path.Combine(dir, "Syncro.exe")))
            {
                DeleteDirectory(dir);
            }
            if (removeData)
            {
                Report(progress, 0.85, "Removing settings", "");
                string data = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), Program.AppName);
                if (Directory.Exists(data)) DeleteDirectory(data);
            }
            Registry.CurrentUser.DeleteSubKeyTree(Program.UninstallKey, false);
            Report(progress, 1.0, "Done", "");
        }

        static void Report(ProgressHandler progress, double fraction, string step, string detail)
        {
            if (progress != null) progress(Math.Max(0, Math.Min(1, fraction)), step, detail);
        }

        static void StopRunning(string dir)
        {
            foreach (Process p in Process.GetProcessesByName(Program.AppName))
            {
                try
                {
                    if (p.Id == Process.GetCurrentProcess().Id) continue;
                    if (Program.IsInside(p.MainModule.FileName, dir))
                    {
                        p.Kill();
                        p.WaitForExit(8000);
                    }
                }
                catch { }
            }
        }

        static void DeleteDirectory(string path)
        {
            for (int attempt = 0; attempt < 6; attempt++)
            {
                try
                {
                    if (Directory.Exists(path)) Directory.Delete(path, true);
                    return;
                }
                catch (IOException) { Thread.Sleep(400); }
                catch (UnauthorizedAccessException) { Thread.Sleep(400); }
            }
            if (Directory.Exists(path)) Directory.Delete(path, true);
        }

        static long DirectorySize(string dir)
        {
            long size = 0;
            try { foreach (string f in Directory.GetFiles(dir, "*", SearchOption.AllDirectories)) size += new FileInfo(f).Length; }
            catch { }
            return size;
        }

        static void TryDelete(string path)
        {
            try { if (File.Exists(path)) File.Delete(path); } catch { }
        }

        static void CreateShortcut(string path, string target, string workingDir)
        {
            Type shellType = Type.GetTypeFromProgID("WScript.Shell");
            object shell = Activator.CreateInstance(shellType);
            try
            {
                object link = shellType.InvokeMember("CreateShortcut", BindingFlags.InvokeMethod, null, shell, new object[] { path });
                try
                {
                    Type t = link.GetType();
                    t.InvokeMember("TargetPath", BindingFlags.SetProperty, null, link, new object[] { target });
                    t.InvokeMember("WorkingDirectory", BindingFlags.SetProperty, null, link, new object[] { workingDir });
                    t.InvokeMember("IconLocation", BindingFlags.SetProperty, null, link, new object[] { target + ",0" });
                    t.InvokeMember("Description", BindingFlags.SetProperty, null, link, new object[] { "Fast, end-to-end encrypted file sharing with your phone" });
                    t.InvokeMember("Save", BindingFlags.InvokeMethod, null, link, null);
                }
                finally { Marshal.FinalReleaseComObject(link); }
            }
            finally { Marshal.FinalReleaseComObject(shell); }
        }
    }

    public class SetupWindow
    {
        public enum Mode { Install, Uninstall }

        public readonly Window Window;
        readonly Mode mode;
        string directory;
        bool busy;
        bool finished;
        bool launchWhenDone;

        public SetupWindow(Mode mode, string directory)
        {
            this.mode = mode;
            using (Stream xaml = Program.Resource("Syncro.Setup.xaml"))
            {
                Window = (Window)XamlReader.Load(xaml);
            }
            ApplyRoboto();
            using (Stream icon = Program.Resource("Syncro.icon.ico"))
            {
                if (icon != null) Window.Icon = System.Windows.Media.Imaging.BitmapFrame.Create(icon, System.Windows.Media.Imaging.BitmapCreateOptions.None, System.Windows.Media.Imaging.BitmapCacheOption.OnLoad);
            }

            Get<TextBlock>("VersionText").Text = "Version " + Program.Version + " · end-to-end encrypted";
            Get<Border>("TitleBar").MouseLeftButtonDown += delegate(object s, MouseButtonEventArgs e) { if (e.ButtonState == MouseButtonState.Pressed) Window.DragMove(); };
            Get<Button>("MinimizeButton").Click += delegate { Window.WindowState = WindowState.Minimized; };
            Get<Button>("CloseButton").Click += delegate { if (!busy) Window.Close(); };
            Get<Button>("CancelButton").Click += delegate { Window.Close(); };
            Get<Button>("KeepButton").Click += delegate { Window.Close(); };
            Get<Button>("FinishButton").Click += delegate { Finish(); };
            Get<Button>("ChangeButton").Click += delegate { ChooseFolder(); };
            Get<Button>("InstallButton").Click += delegate { StartInstall(); };
            Get<Button>("RemoveButton").Click += delegate { StartUninstall(); };

            if (mode == Mode.Uninstall)
            {
                this.directory = directory;
                Get<TextBlock>("WindowTitle").Text = "Remove Syncro";
                Show("UninstallPage");
                return;
            }

            string installed = Program.InstalledLocation();
            this.directory = directory ?? installed ?? Installer.DefaultDirectory();
            Get<TextBlock>("PathText").Text = this.directory;
            if (installed != null)
            {
                string version = Program.InstalledVersion();
                Get<TextBlock>("WelcomeTitle").Text = "Update Syncro";
                Get<TextBlock>("WelcomeSubtitle").Text = (version != null ? "Version " + version + " is installed. " : "Syncro is installed. ") +
                    "Updating keeps your settings, history and trusted devices.";
                Get<Button>("InstallButton").Content = "Update";
                Get<CheckBox>("DesktopShortcut").IsChecked = Installer.HasDesktopShortcut();
                Get<CheckBox>("StartWithWindows").IsChecked = Installer.StartsWithWindows();
            }
            Show("WelcomePage");
        }

        T Get<T>(string name) where T : class
        {
            return (T)Window.FindName(name);
        }

        void Show(string page)
        {
            foreach (string name in new[] { "WelcomePage", "ProgressPage", "DonePage", "UninstallPage" })
            {
                Get<FrameworkElement>(name).Visibility = name == page ? Visibility.Visible : Visibility.Collapsed;
            }
        }

        void ApplyRoboto()
        {
            try
            {
                string dir = Path.Combine(Path.GetTempPath(), "SyncroSetupFonts");
                Directory.CreateDirectory(dir);
                foreach (string weight in new[] { "regular", "medium", "semibold", "bold" })
                {
                    string file = Path.Combine(dir, "roboto_" + weight + ".ttf");
                    if (File.Exists(file)) continue;
                    using (Stream s = Program.Resource("Syncro.font.roboto_" + weight + ".ttf"))
                    {
                        if (s == null) return;
                        using (Stream o = File.Create(file)) s.CopyTo(o);
                    }
                }
                Window.FontFamily = new FontFamily(new Uri(dir.TrimEnd('\\') + "\\"), "./#Roboto");
            }
            catch { }
        }

        void ChooseFolder()
        {
            string picked = FolderPicker.Pick(new System.Windows.Interop.WindowInteropHelper(Window).Handle, directory, "Choose where to install Syncro");
            if (picked == null) return;
            // Keep installs in a dedicated folder so uninstalling never deletes unrelated files.
            if (!string.Equals(Path.GetFileName(picked.TrimEnd('\\')), Program.AppName, StringComparison.OrdinalIgnoreCase))
            {
                picked = Path.Combine(picked, Program.AppName);
            }
            directory = picked;
            Get<TextBlock>("PathText").Text = directory;
        }

        void SetProgress(double fraction, string step, string detail)
        {
            Get<TextBlock>("PercentText").Text = ((int)Math.Round(fraction * 100)) + "%";
            Get<TextBlock>("StepText").Text = step;
            Get<TextBlock>("DetailText").Text = detail;
            var fill = Get<Border>("ProgressFill");
            var track = (FrameworkElement)fill.Parent;
            fill.Width = Math.Max(0, track.ActualWidth * fraction);
        }

        void StartInstall()
        {
            bool desktop = Get<CheckBox>("DesktopShortcut").IsChecked == true;
            bool startup = Get<CheckBox>("StartWithWindows").IsChecked == true;
            launchWhenDone = Get<CheckBox>("LaunchAfter").IsChecked == true;
            bool update = Program.InstalledLocation() != null;
            Get<TextBlock>("ProgressTitle").Text = update ? "Updating Syncro…" : "Installing Syncro…";
            Show("ProgressPage");
            busy = true;
            string dir = directory;
            RunInBackground(
                delegate { Installer.Install(dir, desktop, startup, Progress); },
                delegate
                {
                    busy = false;
                    finished = true;
                    Get<TextBlock>("DoneTitle").Text = update ? "Syncro is up to date" : "Syncro is ready";
                    Get<Button>("FinishButton").Content = launchWhenDone ? "Open Syncro" : "Finish";
                    Show("DonePage");
                });
        }

        void StartUninstall()
        {
            bool removeData = Get<CheckBox>("RemoveData").IsChecked == true;
            Get<TextBlock>("ProgressTitle").Text = "Removing Syncro…";
            Get<TextBlock>("ProgressSubtitle").Text = "Closing the app and cleaning up.";
            Show("ProgressPage");
            busy = true;
            string dir = directory;
            RunInBackground(
                delegate { Installer.Uninstall(dir, removeData, Progress); },
                delegate
                {
                    busy = false;
                    finished = true;
                    launchWhenDone = false;
                    Get<TextBlock>("DoneTitle").Text = "Syncro was removed";
                    Get<TextBlock>("DoneBody").Text = removeData
                        ? "Syncro, its settings and device keys are gone. Files you received are still in your Downloads folder."
                        : "Syncro is gone. Your settings are kept in case you install it again, and received files are still in Downloads.";
                    Get<Border>("FirewallCard").Visibility = Visibility.Collapsed;
                    Get<TextBlock>("DoneGlyph").Text = "";
                    Get<Button>("FinishButton").Content = "Close";
                    Show("DonePage");
                });
        }

        void Progress(double fraction, string step, string detail)
        {
            Window.Dispatcher.BeginInvoke(new Action(delegate { SetProgress(fraction, step, detail); }));
        }

        void RunInBackground(Action work, Action done)
        {
            var thread = new Thread(delegate()
            {
                Exception error = null;
                try { work(); }
                catch (Exception e) { error = e; }
                Window.Dispatcher.BeginInvoke(new Action(delegate
                {
                    if (error == null) done();
                    else ShowError(error);
                }));
            });
            thread.IsBackground = true;
            thread.Start();
        }

        void ShowError(Exception error)
        {
            busy = false;
            finished = true;
            launchWhenDone = false;
            var danger = (Brush)Window.FindResource("Danger");
            Get<Border>("DoneBubble").Background = (Brush)Window.FindResource("DangerWash");
            Get<TextBlock>("DoneGlyph").Text = "";
            Get<TextBlock>("DoneGlyph").Foreground = danger;
            Get<TextBlock>("DoneTitle").Text = "Setup couldn't finish";
            string message = error is UnauthorizedAccessException || error is IOException
                ? "A file is in use or the folder isn't writable. Close Syncro and try again. (" + error.Message + ")"
                : error.Message;
            Get<TextBlock>("DoneBody").Text = message;
            Get<Border>("FirewallCard").Visibility = Visibility.Collapsed;
            Get<Button>("FinishButton").Content = "Close";
            Show("DonePage");
        }

        void Finish()
        {
            if (finished && launchWhenDone)
            {
                try { Process.Start(new ProcessStartInfo(Path.Combine(directory, "Syncro.exe")) { WorkingDirectory = directory }); }
                catch { }
            }
            if (mode == Mode.Uninstall) Program.ScheduleSelfDelete();
            Window.Close();
        }

        // Keep the unused-field warning away when compiling without the installer pages.
        public bool IsBusy { get { return busy; } }
    }

    /// <summary>The modern Windows folder picker (IFileOpenDialog with FOS_PICKFOLDERS).</summary>
    public static class FolderPicker
    {
        [ComImport, Guid("DC1C5A9C-E88A-4dde-A5A1-60F82A20AEF7")]
        class FileOpenDialogCoClass { }

        [ComImport, Guid("42f85136-db7e-439c-85f1-e4075d135fc8"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
        interface IFileOpenDialog
        {
            [PreserveSig] int Show(IntPtr parent);
            void SetFileTypes(uint count, IntPtr specs);
            void SetFileTypeIndex(uint index);
            void GetFileTypeIndex(out uint index);
            void Advise(IntPtr events, out uint cookie);
            void Unadvise(uint cookie);
            void SetOptions(uint options);
            void GetOptions(out uint options);
            void SetDefaultFolder(IShellItem item);
            void SetFolder(IShellItem item);
            void GetFolder(out IShellItem item);
            void GetCurrentSelection(out IShellItem item);
            void SetFileName([MarshalAs(UnmanagedType.LPWStr)] string name);
            void GetFileName([MarshalAs(UnmanagedType.LPWStr)] out string name);
            void SetTitle([MarshalAs(UnmanagedType.LPWStr)] string title);
            void SetOkButtonLabel([MarshalAs(UnmanagedType.LPWStr)] string text);
            void SetFileNameLabel([MarshalAs(UnmanagedType.LPWStr)] string label);
            void GetResult(out IShellItem item);
        }

        [ComImport, Guid("43826D1E-E718-42EE-BC55-A1E261C37BFE"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
        interface IShellItem
        {
            void BindToHandler(IntPtr bindContext, ref Guid handler, ref Guid riid, out IntPtr result);
            void GetParent(out IShellItem parent);
            void GetDisplayName(uint sigdn, [MarshalAs(UnmanagedType.LPWStr)] out string name);
            void GetAttributes(uint mask, out uint attributes);
            void Compare(IShellItem other, uint hint, out int order);
        }

        [DllImport("shell32.dll", CharSet = CharSet.Unicode, PreserveSig = false)]
        static extern void SHCreateItemFromParsingName([MarshalAs(UnmanagedType.LPWStr)] string path, IntPtr bindContext, [MarshalAs(UnmanagedType.LPStruct)] Guid riid, out IShellItem item);

        const uint FOS_PICKFOLDERS = 0x20;
        const uint FOS_FORCEFILESYSTEM = 0x40;
        const uint SIGDN_FILESYSPATH = 0x80058000;

        public static string Pick(IntPtr owner, string initial, string title)
        {
            var dialog = (IFileOpenDialog)new FileOpenDialogCoClass();
            try
            {
                dialog.SetOptions(FOS_PICKFOLDERS | FOS_FORCEFILESYSTEM);
                dialog.SetTitle(title);
                string start = initial;
                while (!string.IsNullOrEmpty(start) && !Directory.Exists(start)) start = Path.GetDirectoryName(start);
                if (!string.IsNullOrEmpty(start))
                {
                    try
                    {
                        IShellItem folder;
                        SHCreateItemFromParsingName(start, IntPtr.Zero, typeof(IShellItem).GUID, out folder);
                        dialog.SetFolder(folder);
                    }
                    catch { }
                }
                if (dialog.Show(owner) != 0) return null;
                IShellItem result;
                dialog.GetResult(out result);
                string path;
                result.GetDisplayName(SIGDN_FILESYSPATH, out path);
                return path;
            }
            catch { return null; }
            finally { Marshal.FinalReleaseComObject(dialog); }
        }
    }
}
