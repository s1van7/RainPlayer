; RainPlayer Windows 安装包脚本（Inno Setup 6）
; 由 scripts\package.ps1 调用，也可直接：
;   iscc /DMyAppVersion=1.0.0 /DSourceDir=..\dist /DOutputDir=..\release scripts\installer.iss

#ifndef MyAppVersion
  #define MyAppVersion "1.0.0"
#endif
#ifndef SourceDir
  #define SourceDir "..\dist"
#endif
#ifndef OutputDir
  #define OutputDir "..\release"
#endif

#define MyAppName "RainPlayer"
#define MyAppPublisher "RainPlayer"
#define MyAppExeName "RainPlayer.exe"
#define MyAppUrl "https://github.com/s1van7/RainPlayer"

[Setup]
AppId={{7C4E1F62-9A3D-4B58-8E21-5D6A0C7B9E34}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppVerName={#MyAppName} {#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppUrl}
AppSupportURL={#MyAppUrl}
AppUpdatesURL={#MyAppUrl}/releases
DefaultDirName={autopf}\{#MyAppName}
DefaultGroupName={#MyAppName}
DisableProgramGroupPage=yes
LicenseFile=..\LICENSE
OutputDir={#OutputDir}
OutputBaseFilename=RainPlayer-{#MyAppVersion}-win-x64-setup
SetupIconFile=..\src\RainPlayer.App\Assets\app.ico
Compression=lzma2/max
SolidCompression=yes
WizardStyle=modern
ArchitecturesAllowed=x64
ArchitecturesInstallIn64BitMode=x64
MinVersion=10.0
UninstallDisplayIcon={app}\{#MyAppExeName}
; 不弹「为所有用户/仅我」的前置框（那个框的按钮是英文的），
; 需要时用 /ALLUSERS 或 /CURRENTUSER 命令行参数指定
PrivilegesRequiredOverridesAllowed=commandline
; 多语言时显示语言选择框，默认停在第一个（简体中文）
ShowLanguageDialog=yes
; 不按系统语言自动挑，一律默认简体中文
LanguageDetectionMethod=none

[Languages]
; 语言包随仓库一起带（scripts\ChineseSimplified.isl），不依赖 Inno 安装目录，
; 并且里面显式指定了 Microsoft YaHei UI，避免汉字渲染成方块
Name: "chinesesimplified"; MessagesFile: "ChineseSimplified.isl"
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "创建桌面快捷方式"; GroupDescription: "附加任务："; Flags: unchecked

[Files]
Source: "{#SourceDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"
Name: "{group}\卸载 {#MyAppName}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "立即运行 {#MyAppName}"; Flags: nowait postinstall skipifsilent

[UninstallDelete]
Type: filesandordirs; Name: "{app}"
