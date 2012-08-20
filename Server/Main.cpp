#include "Server.h"

#include "resource.h"

using namespace ts;

// Constants.
const wchar_t * RegistryKey = L"Software\\Things & Stuff\\Touchpad Server";
const OUTPUT_LEVEL OutputLevel = OL_INFO;

// Notification icon.
NOTIFYICONDATA Notify;
// Port.
int Port = DefaultPort;
// Password hash.
int Password = 0;

// Server thread.
Server server;

// Log and message control
HWND LogWnd = NULL;
HWND StatusWnd = NULL;

// Compute hash of string.
int Hash(const wchar_t * str);

// Load preferences from globals.
void LoadPreferences(HWND hWnd)
{
	HKEY key;	
	if(RegCreateKeyEx(HKEY_CURRENT_USER, RegistryKey, 0, NULL, 0, KEY_READ, NULL, &key, NULL) == ERROR_SUCCESS)
	{
		DWORD dwType, dwSize;

		dwType = REG_DWORD;
		dwSize = sizeof(DWORD);
		RegQueryValueEx(key, L"Port", NULL, &dwType, (BYTE *)&Port, &dwSize);

		dwType = REG_DWORD;
		dwSize = sizeof(DWORD);
		RegQueryValueEx(key, L"Password", NULL, &dwType, (BYTE *)&Password, &dwSize);

		RegCloseKey(key);
	}

	SetDlgItemInt(hWnd, IDC_PORT, Port, FALSE);
	if(Password != 0)
	{
		SetDlgItemText(hWnd, IDC_PASSWORD1, L"*****");
		SetDlgItemText(hWnd, IDC_PASSWORD2, L"*****");
	}
	else
	{
		SetDlgItemText(hWnd, IDC_PASSWORD1, L"");
		SetDlgItemText(hWnd, IDC_PASSWORD2, L"");
	}
}

// Validate and save preferences to globals.
bool SavePreferences(HWND hWnd)
{
	wchar_t password1[256] = { 0 };
	wchar_t password2[256] = { 0 };

	// Validation.
	GetDlgItemText(hWnd, IDC_PASSWORD1, password1, sizeof(password1) / sizeof(password1[0]));
	GetDlgItemText(hWnd, IDC_PASSWORD2, password2, sizeof(password2) / sizeof(password2[0]));

	if(wcscmp(password1, password2) != 0)
	{
		MessageBox(hWnd, L"Password does not match. Please re-enter your password.", L"Error", MB_OK | MB_ICONERROR);
		SetFocus(GetDlgItem(hWnd, IDC_PASSWORD1));
		return false;
	}
	int password = Password;
	if(wcscmp(password1, L"") == 0)
		password = 0;
	else if(wcscmp(password1, L"*****") != 0)
		password = Hash(password1);

	int port = GetDlgItemInt(hWnd, IDC_PORT, NULL, FALSE);
	if(port <= 0 || port > 65535)
	{
		MessageBox(hWnd, L"Port must be a number between 1 and 65535.", L"Error", MB_OK | MB_ICONERROR);
		SetFocus(GetDlgItem(hWnd, IDC_PORT));
		return false;
	}

	// Validation passed, set options.
	bool init = !server.IsRunning();
	if(port != Port)
		init = true;
	if(password != Password)
		init = true;

	if(init && !server.Run(port, password))
	{
		MessageBox(hWnd, L"Error starting server!", L"Error", MB_OK | MB_ICONERROR);
		return false;
	}

	Port = port;
	Password = password;

	// Write preferences to the registry
	HKEY key;
	if(RegCreateKeyEx(HKEY_CURRENT_USER, RegistryKey, 0, NULL, 0, KEY_WRITE, NULL, &key, NULL) == ERROR_SUCCESS)
	{
		DWORD dwType, dwSize;

		dwType = REG_DWORD;
		dwSize = sizeof(DWORD);
		RegSetValueEx(key, L"Port", 0, dwType, (BYTE *)&Port, dwSize);
		
		dwType = REG_DWORD;
		dwSize = sizeof(DWORD);
		RegSetValueEx(key, L"Password", 0, dwType, (BYTE *)&Password, dwSize);

		RegCloseKey(key);
	}

	return true;
}

// Show log and resize window based on check box.
void ShowLog(HWND hWnd)
{
	bool show = IsDlgButtonChecked(hWnd, IDC_SHOWLOG) == BST_CHECKED;
	ShowWindow(LogWnd, show ? SW_SHOW : SW_HIDE);

	RECT rcLog;
	GetWindowRect(LogWnd, &rcLog);
	ScreenToClient(hWnd, (POINT *)&rcLog + 0);
	ScreenToClient(hWnd, (POINT *)&rcLog + 1);

	RECT rcWnd, rcClient;
	GetWindowRect(hWnd, &rcWnd);

	GetClientRect(hWnd, &rcClient);
	ClientToScreen(hWnd, (POINT *)&rcClient + 0);
	ClientToScreen(hWnd, (POINT *)&rcClient + 1);

	MoveWindow(
		hWnd, 
		rcWnd.left, 
		rcWnd.top, 
		rcWnd.right - rcWnd.left, 
		(show ? rcLog.bottom + 12 : rcLog.top) + rcClient.top - rcWnd.top,
		TRUE);
}

// Notify message handler.
BOOL NotifyProc(HWND hWnd, WPARAM wParam, LPARAM lParam)
{
	// Message from the notification icon.
	switch(LOWORD(lParam))
	{
	case NIN_BALLOONUSERCLICK:
		ShowWindow(hWnd, SW_SHOW);
	case NIN_BALLOONHIDE:
	case NIN_BALLOONTIMEOUT:
		Notify.uFlags = NIF_INFO;
		Notify.dwInfoFlags = 0;
		wcscpy_s(Notify.szInfoTitle, L"");
		wcscpy_s(Notify.szInfo, L"");

		Shell_NotifyIcon(NIM_MODIFY, &Notify);
		return TRUE;
		
	case WM_LBUTTONDBLCLK:
		ShowWindow(hWnd, SW_SHOW);
		SetForegroundWindow(hWnd);
		return TRUE;
	case WM_RBUTTONDOWN:
	case WM_CONTEXTMENU:
		// Show context menu at icon.
		POINT pt;
		GetCursorPos(&pt);

		HMENU menu = LoadMenu(NULL, MAKEINTRESOURCE(IDR_MENU));
		HMENU context = GetSubMenu(menu, 0);
		TrackPopupMenu(context, 0, pt.x, pt.y, 0, hWnd, NULL);
		DestroyMenu(menu);

		Shell_NotifyIcon(NIM_SETFOCUS, &Notify);
		return TRUE;
	}
	return FALSE;
}

// Dialog message handler.
BOOL CALLBACK DialogProc(HWND hWnd, UINT msg, WPARAM wParam, LPARAM lParam)
{
	switch(msg)
	{
	case WM_INITDIALOG:	
		// Get the log control.
		LogWnd = GetDlgItem(hWnd, IDC_LOG);
		StatusWnd = GetDlgItem(hWnd, IDC_STATUS);
		
		// Add icon to notification area.
		memset(&Notify, 0, sizeof(Notify));
		Notify.cbSize = sizeof(Notify);
		Notify.hWnd = hWnd;
		Notify.uID = 1;
		Notify.uFlags = NIF_ICON | NIF_MESSAGE;
		Notify.uCallbackMessage = WM_APP;
		Notify.uVersion = NOTIFYICON_VERSION_4;
		Notify.hIcon = (HICON)LoadImage(
			GetModuleHandle(NULL), 
			MAKEINTRESOURCE(IDI_ICON),
			IMAGE_ICON, 
			GetSystemMetrics(SM_CXSMICON), 
			GetSystemMetrics(SM_CYSMICON), 
			0);
		Shell_NotifyIcon(NIM_ADD, &Notify);
		
		SendMessage(hWnd, WM_SETICON, ICON_SMALL, (LPARAM)Notify.hIcon);
		SendMessage(hWnd, WM_SETICON, ICON_BIG, (LPARAM)LoadImage(
			GetModuleHandle(NULL), 
			MAKEINTRESOURCE(IDI_ICON),
			IMAGE_ICON, 
			GetSystemMetrics(SM_CXICON), 
			GetSystemMetrics(SM_CYICON), 
			0));

		LoadPreferences(hWnd);
		server.Run(Port, Password);
		return TRUE;

	case WM_SHOWWINDOW:
		if(wParam)
		{
			ShowLog(hWnd);
			LoadPreferences(hWnd);
		}
		return TRUE;

	case WM_APP:
		return NotifyProc(hWnd, wParam, lParam);

	case WM_COMMAND:
		switch(LOWORD(wParam))
		{
		case IDC_SHOWLOG:
			ShowLog(hWnd);
			return TRUE;
		case IDOK:
			if(!SavePreferences(hWnd))
				return TRUE;
		case IDCANCEL:
			ShowWindow(hWnd, SW_HIDE);
			return TRUE;
		case ID_CONTEXT_EXIT:
			DestroyWindow(hWnd);
			return TRUE;
		case ID_CONTEXT_RESTARTSERVER:
			LoadPreferences(hWnd);
			server.Run(Port, Password);
			return TRUE;
		case ID_CONTEXT_SETTINGS:
			ShowWindow(hWnd, SW_SHOW);
			SetForegroundWindow(hWnd);
			return TRUE;
		}
		return FALSE;

	case WM_CLOSE:
		ShowWindow(hWnd, SW_HIDE);
		return TRUE;

	case WM_DESTROY:
		Shell_NotifyIcon(NIM_DELETE, &Notify);
		PostQuitMessage(0);
		return TRUE;
	}
	return FALSE;
}

// Entry point.
int CALLBACK WinMain(HINSTANCE, HINSTANCE, LPSTR lpCmdLine, int)
{	
	InitSockets();

	HWND hWnd = CreateDialog(NULL, MAKEINTRESOURCE(IDD_DIALOG), NULL, DialogProc);
	
	MSG msg;
	while(GetMessage(&msg, NULL, 0, 0))
	{
		// Handle window messages.
		if(!IsDialogMessage(hWnd, &msg))
		{
			TranslateMessage(&msg);
			DispatchMessage(&msg);
		}
	}

	CloseSockets();
	return 0;
}

// Log a message.
void Log(int level, const wchar_t * s, ...)
{
	// Get status and level.
	bool notify = (level & OL_NOTIFY) != 0;
	bool status = (level & OL_STATUS) != 0;
	level &= 0x0FFFFFFF;

	int nSize = 0;
	wchar_t buffer[1024] = { 0 };
	va_list args;
	va_start(args, s);
	nSize = _vsnwprintf_s(buffer, sizeof(buffer) / sizeof(buffer[0]) - 1, s, args);
	va_end(args);

	// Status goes to tooltip and status window.
	if(status)
	{
		// Set tooltip.
		Notify.uFlags = NIF_TIP;
		Notify.szTip[0] = 0;
		wcscpy_s(Notify.szTip, buffer);
		Shell_NotifyIcon(NIM_MODIFY, &Notify);

		// Set status window.
		SetWindowText(StatusWnd, buffer);
	}
	
	// Show notification balloon if notify.
	if(notify)
	{
		Notify.uFlags = NIF_INFO;
		switch(level)
		{
		case OL_INFO:		Notify.dwInfoFlags = NIIF_INFO; break;
		case OL_ERROR:		Notify.dwInfoFlags = NIIF_ERROR; break;
		case OL_WARNING:	Notify.dwInfoFlags = NIIF_WARNING; break;
		}
		wcscpy_s(Notify.szInfoTitle, L"Touchpad Server");
		wcscpy_s(Notify.szInfo, buffer);

		Shell_NotifyIcon(NIM_MODIFY, &Notify);
	}

	if(level > OutputLevel)
		return;

	int length = GetWindowTextLength(LogWnd) + sizeof(buffer) / sizeof(buffer[0]) + 16;
	wchar_t * text = new wchar_t[length];
	GetWindowText(LogWnd, text, length);
	switch(level)
	{
	case OL_ERROR:		wcscat_s(text, length, L"ERROR: "); break;
	case OL_WARNING:	wcscat_s(text, length, L"WARNING: "); break;
	case OL_VERBOSE:	wcscat_s(text, length, L"VERBOSE: "); break;
	}
	wcscat_s(text, length, buffer);
	SetWindowText(LogWnd, text);
	SendMessage(LogWnd, EM_LINESCROLL, 0, 1000000);

	InvalidateRect(LogWnd, NULL, FALSE);

	delete [] text;
}

// http://www.cse.yorku.ca/~oz/hash.html
int Hash(const wchar_t * str)
{
	int hash = 5381;
	int c;
	while(c = *str++)
		hash = hash * 33 + c;

	return hash;
}
