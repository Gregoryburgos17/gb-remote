// GB Remote for Windows: connects this computer with the GB platform.
//
// Built for old computers: .NET Framework 4.5 (included in Windows 8 and later),
// one executable, no installer, no dependencies. Big letters, one field.
//
// Flow: the user writes the ID of the company -> the app sends the details of
// the computer and shows its device code -> support accepts or rejects it in
// GB Core (Support > Devices) -> the app shows the result and opens the system.
//
// Build: csc /target:winexe /platform:anycpu /out:GBRemote.exe
//        /r:System.Management.dll /r:System.Web.Extensions.dll GBRemote.cs
using System;
using System.Collections.Generic;
using System.Drawing;
using System.IO;
using System.Management;
using System.Net;
using System.Security.Cryptography;
using System.Text;
using System.Web.Script.Serialization;
using System.Windows.Forms;
using Microsoft.Win32;

[assembly: System.Reflection.AssemblyTitle("GB Remote")]
[assembly: System.Reflection.AssemblyProduct("GB Remote")]
[assembly: System.Reflection.AssemblyVersion("0.1.2.0")]

namespace GBRemote
{
    static class Program
    {
        public const string Version = "0.1.2";
        // Address of the GB platform (the CI replaces it with the GitHub variable
        // GBREMOTE_SERVER). Can be changed with a file "servidor.txt" next to the
        // program (one line). Never put here a domain that GB does not own: the app
        // sends the details of the computer to it.
        public const string DefaultServer = "http://26.117.63.23:8069";

        [STAThread]
        static void Main()
        {
            // Windows 8 / .NET 4.5 do not enable TLS 1.2 by default
            ServicePointManager.SecurityProtocol = (SecurityProtocolType)3072 | SecurityProtocolType.Tls;
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);
            Application.Run(new MainForm());
        }
    }

    class Settings
    {
        public string Server;
        public string Company;
        public string DeviceCode;
        public string Secret;

        static string Folder
        {
            get { return Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "GBRemote"); }
        }

        static string FilePath { get { return Path.Combine(Folder, "config.json"); } }

        public static Settings Load()
        {
            var settings = new Settings();
            try
            {
                if (File.Exists(FilePath))
                {
                    var data = new JavaScriptSerializer().Deserialize<Dictionary<string, string>>(File.ReadAllText(FilePath));
                    string value;
                    if (data.TryGetValue("server", out value)) settings.Server = value;
                    if (data.TryGetValue("company", out value)) settings.Company = value;
                    if (data.TryGetValue("device_code", out value)) settings.DeviceCode = value;
                    if (data.TryGetValue("secret", out value)) settings.Secret = value;
                }
            }
            catch (Exception) { }
            string local = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "servidor.txt");
            if (File.Exists(local))
            {
                string line = File.ReadAllText(local).Trim();
                if (line.Length > 0) settings.Server = line;
            }
            if (string.IsNullOrEmpty(settings.Server)) settings.Server = Program.DefaultServer;
            if (string.IsNullOrEmpty(settings.Secret)) settings.Secret = NewSecret();
            return settings;
        }

        public void Save()
        {
            Directory.CreateDirectory(Folder);
            var data = new Dictionary<string, string> {
                { "server", Server }, { "company", Company ?? "" },
                { "device_code", DeviceCode ?? "" }, { "secret", Secret },
            };
            File.WriteAllText(FilePath, new JavaScriptSerializer().Serialize(data));
        }

        static string NewSecret()
        {
            var bytes = new byte[32];
            using (var rng = new RNGCryptoServiceProvider()) rng.GetBytes(bytes);
            return Convert.ToBase64String(bytes).Replace('+', '-').Replace('/', '_').TrimEnd('=');
        }
    }

    static class Api
    {
        public static Dictionary<string, object> Post(string server, string path, Dictionary<string, object> body)
        {
            var serializer = new JavaScriptSerializer { MaxJsonLength = 4 * 1024 * 1024 };
            byte[] data = Encoding.UTF8.GetBytes(serializer.Serialize(body));
            var request = (HttpWebRequest)WebRequest.Create(server.TrimEnd('/') + "/gb_remote/api/v1/" + path);
            request.Method = "POST";
            request.ContentType = "application/json";
            request.UserAgent = "GBRemote/" + Program.Version + " (Windows)";
            request.Timeout = 20000;
            request.ContentLength = data.Length;
            using (var stream = request.GetRequestStream()) stream.Write(data, 0, data.Length);
            string text;
            try
            {
                using (var response = (HttpWebResponse)request.GetResponse())
                using (var reader = new StreamReader(response.GetResponseStream(), Encoding.UTF8))
                    text = reader.ReadToEnd();
            }
            catch (WebException exc)
            {
                if (exc.Response == null) throw;
                using (var reader = new StreamReader(exc.Response.GetResponseStream(), Encoding.UTF8))
                    text = reader.ReadToEnd();
            }
            try
            {
                var answer = serializer.Deserialize<Dictionary<string, object>>(text);
                if (answer != null) return answer;
            }
            catch (ArgumentException) { }
            catch (InvalidOperationException) { }
            throw new NotGbServerException();
        }

        /// <summary>True when the address answers like the GB platform. Asked before sending
        /// any detail of the computer, so a wrong address never receives them.</summary>
        public static bool IsGb(string server)
        {
            try
            {
                var answer = Post(server, "status", new Dictionary<string, object> { { "device_code", "" }, { "secret", "" } });
                object error;
                return answer.TryGetValue("error", out error)
                    && (Convert.ToString(error) == "unknown_device" || Convert.ToString(error) == "too_many_requests");
            }
            catch (NotGbServerException) { return false; }
        }
    }

    class NotGbServerException : Exception
    {
        public NotGbServerException() : base("La dirección configurada no es de GB.") { }
    }

    static class SystemDetails
    {
        static string Wmi(string query, string property)
        {
            try
            {
                using (var searcher = new ManagementObjectSearcher(query))
                    foreach (ManagementObject item in searcher.Get())
                        return Convert.ToString(item[property]);
            }
            catch (Exception) { }
            return "";
        }

        static List<string> WmiList(string query, string property)
        {
            var values = new List<string>();
            try
            {
                using (var searcher = new ManagementObjectSearcher(query))
                    foreach (ManagementObject item in searcher.Get())
                        values.Add(Convert.ToString(item[property]));
            }
            catch (Exception) { }
            return values;
        }

        static Dictionary<string, object> Browser(string name, string exe)
        {
            foreach (var hive in new[] { Registry.LocalMachine, Registry.CurrentUser })
            {
                try
                {
                    using (var key = hive.OpenSubKey(@"SOFTWARE\Microsoft\Windows\CurrentVersion\App Paths\" + exe))
                    {
                        string path = key == null ? null : key.GetValue("") as string;
                        if (!string.IsNullOrEmpty(path) && File.Exists(path.Trim('"')))
                        {
                            var info = System.Diagnostics.FileVersionInfo.GetVersionInfo(path.Trim('"'));
                            return new Dictionary<string, object> { { "name", name }, { "version", info.ProductVersion } };
                        }
                    }
                }
                catch (Exception) { }
            }
            return null;
        }

        public static Dictionary<string, object> Collect()
        {
            var browsers = new List<object>();
            foreach (var pair in new[] {
                new[] { "Google Chrome", "chrome.exe" }, new[] { "Mozilla Firefox", "firefox.exe" },
                new[] { "Microsoft Edge", "msedge.exe" }, new[] { "Internet Explorer", "iexplore.exe" } })
            {
                var browser = Browser(pair[0], pair[1]);
                if (browser != null) browsers.Add(browser);
            }
            long memory = 0;
            long.TryParse(Wmi("SELECT TotalPhysicalMemory FROM Win32_ComputerSystem", "TotalPhysicalMemory"), out memory);
            long diskSize = 0, diskFree = 0;
            long.TryParse(Wmi("SELECT Size FROM Win32_LogicalDisk WHERE DeviceID='C:'", "Size"), out diskSize);
            long.TryParse(Wmi("SELECT FreeSpace FROM Win32_LogicalDisk WHERE DeviceID='C:'", "FreeSpace"), out diskFree);
            var screen = Screen.PrimaryScreen.Bounds;
            return new Dictionary<string, object> {
                { "app_version", Program.Version },
                { "os", new Dictionary<string, object> {
                    { "name", Wmi("SELECT Caption FROM Win32_OperatingSystem", "Caption").Trim() },
                    { "version", Wmi("SELECT Version FROM Win32_OperatingSystem", "Version") },
                    { "build", Wmi("SELECT BuildNumber FROM Win32_OperatingSystem", "BuildNumber") },
                    { "architecture", Environment.Is64BitOperatingSystem ? "64-bit" : "32-bit" },
                } },
                { "computer", new Dictionary<string, object> {
                    { "manufacturer", Wmi("SELECT Manufacturer FROM Win32_ComputerSystem", "Manufacturer") },
                    { "model", Wmi("SELECT Model FROM Win32_ComputerSystem", "Model") },
                    { "processor", Wmi("SELECT Name FROM Win32_Processor", "Name").Trim() },
                    { "cores", Environment.ProcessorCount },
                } },
                { "memory_mb", memory / (1024 * 1024) },
                { "disk_c_gb", diskSize / (1024L * 1024 * 1024) },
                { "disk_c_free_gb", diskFree / (1024L * 1024 * 1024) },
                { "browsers", browsers },
                { "printers", WmiList("SELECT Name FROM Win32_Printer", "Name") },
                { "default_printer", Wmi("SELECT Name FROM Win32_Printer WHERE Default=TRUE", "Name") },
                { "screen", screen.Width + "x" + screen.Height },
                { "dotnet", Environment.Version.ToString() },
                { "user", Environment.UserName },
                { "timezone", TimeZone.CurrentTimeZone.StandardName },
            };
        }
    }

    class MainForm : Form
    {
        readonly Settings settings = Settings.Load();
        readonly Label title = new Label();
        readonly Label message = new Label();
        readonly Label code = new Label();
        readonly TextBox company = new TextBox();
        readonly Button connect = new Button();
        readonly Button open = new Button();
        readonly Timer poll = new Timer { Interval = 10000 };
        string companyUrl = "";

        public MainForm()
        {
            Text = "GB Remote";
            ClientSize = new Size(560, 420);
            StartPosition = FormStartPosition.CenterScreen;
            FormBorderStyle = FormBorderStyle.FixedSingle;
            MaximizeBox = false;
            BackColor = Color.FromArgb(15, 23, 42);
            ForeColor = Color.White;
            Font = new Font("Segoe UI", 14F);

            title.Text = "GB Remote";
            title.Font = new Font("Segoe UI", 26F, FontStyle.Bold);
            title.SetBounds(20, 20, 520, 50);
            title.TextAlign = ContentAlignment.MiddleCenter;

            message.SetBounds(30, 80, 500, 70);
            message.TextAlign = ContentAlignment.MiddleCenter;

            company.SetBounds(80, 160, 400, 40);
            company.Font = new Font("Segoe UI", 18F);
            company.TextAlign = HorizontalAlignment.Center;

            code.SetBounds(30, 150, 500, 70);
            code.Font = new Font("Consolas", 34F, FontStyle.Bold);
            code.TextAlign = ContentAlignment.MiddleCenter;
            code.ForeColor = Color.FromArgb(96, 165, 250);

            StyleButton(connect, "Conectar", Color.FromArgb(37, 99, 235));
            connect.SetBounds(140, 240, 280, 60);
            connect.Click += delegate { Register(); };

            StyleButton(open, "Abrir mi sistema", Color.FromArgb(22, 163, 74));
            open.SetBounds(140, 240, 280, 60);
            open.Click += delegate { if (companyUrl.Length > 0) System.Diagnostics.Process.Start(companyUrl); };

            poll.Tick += delegate { CheckStatus(); };
            Controls.AddRange(new Control[] { title, message, company, code, connect, open });
            AcceptButton = connect;

            if (!string.IsNullOrEmpty(settings.DeviceCode)) { ShowWaiting(); CheckStatus(); }
            else ShowAskCompany();
        }

        static void StyleButton(Button button, string text, Color color)
        {
            button.Text = text;
            button.FlatStyle = FlatStyle.Flat;
            button.FlatAppearance.BorderSize = 0;
            button.BackColor = color;
            button.ForeColor = Color.White;
            button.Font = new Font("Segoe UI", 16F, FontStyle.Bold);
            button.Cursor = Cursors.Hand;
        }

        void ShowAskCompany(string error = null)
        {
            message.Text = error ?? "Escriba el ID de su empresa\n(se lo da GB, por ejemplo: farmacia-moma)";
            message.ForeColor = error == null ? Color.White : Color.FromArgb(252, 165, 165);
            company.Visible = connect.Visible = true;
            if (company.Text.Trim().Length == 0) company.Text = settings.Company ?? "";  // keep what was typed
            code.Visible = open.Visible = false;
            poll.Stop();
        }

        void ShowWaiting()
        {
            message.Text = "Esperando que GB apruebe esta computadora.\nSi le llaman, diga este código:";
            message.ForeColor = Color.White;
            code.Text = FormatCode(settings.DeviceCode);
            code.Visible = true;
            company.Visible = connect.Visible = open.Visible = false;
            poll.Start();
        }

        static string FormatCode(string value)
        {
            value = value ?? "";
            return value.Length == 9 ? value.Substring(0, 3) + " " + value.Substring(3, 3) + " " + value.Substring(6) : value;
        }

        void Register()
        {
            string ref_ = company.Text.Trim();
            if (ref_.Length == 0) { ShowAskCompany("Escriba el ID de su empresa."); return; }
            Cursor = Cursors.WaitCursor;
            message.Text = "Conectando…";
            Application.DoEvents();
            try
            {
                if (!Api.IsGb(settings.Server))
                {
                    ShowAskCompany("La dirección configurada no es de GB.\nLlame a soporte (no se envió nada).");
                    return;
                }
                var answer = Api.Post(settings.Server, "register", new Dictionary<string, object> {
                    { "company", ref_ }, { "computer", Environment.MachineName },
                    { "secret", settings.Secret }, { "info", SystemDetails.Collect() },
                });
                if (answer.ContainsKey("error"))
                {
                    ShowAskCompany(Convert.ToString(answer["error"]) == "company_not_found"
                        ? "No encontramos esa empresa. Revise el ID o llame a GB."
                        : "GB no aceptó la solicitud (" + answer["error"] + ").");
                    return;
                }
                settings.Company = ref_;
                settings.DeviceCode = Convert.ToString(answer["device_code"]);
                settings.Save();
                Apply(answer);
            }
            catch (Exception exc)
            {
                ShowAskCompany("No hay conexión con GB. Revise el internet.\n(" + exc.Message + ")");
            }
            finally { Cursor = Cursors.Default; }
        }

        void CheckStatus()
        {
            try
            {
                var answer = Api.Post(settings.Server, "status", new Dictionary<string, object> {
                    { "device_code", settings.DeviceCode }, { "secret", settings.Secret },
                });
                if (answer.ContainsKey("error")) { settings.DeviceCode = ""; settings.Save(); ShowAskCompany(); return; }
                Apply(answer);
            }
            catch (Exception)
            {
                message.Text = "Sin internet. Se reintenta solo…";
            }
        }

        void Apply(Dictionary<string, object> answer)
        {
            string state = Convert.ToString(answer["state"]);
            if (state == "approved")
            {
                poll.Stop();
                companyUrl = answer.ContainsKey("url") ? Convert.ToString(answer["url"]) : "";
                message.Text = "Conectado a " + (answer.ContainsKey("company") ? answer["company"] : "GB");
                message.ForeColor = Color.FromArgb(134, 239, 172);
                code.Text = FormatCode(settings.DeviceCode);
                code.Visible = open.Visible = true;
                company.Visible = connect.Visible = false;
            }
            else if (state == "rejected" || state == "revoked")
            {
                poll.Stop();
                settings.DeviceCode = "";
                settings.Save();
                ShowAskCompany(state == "rejected"
                    ? "GB no aprobó esta computadora. Llame a soporte."
                    : "Esta computadora fue desconectada. Llame a soporte.");
            }
            else ShowWaiting();
        }
    }
}
