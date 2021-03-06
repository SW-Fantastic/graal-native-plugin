#include"PlatformNatives.h"
#include<Windows.h>
#include<tchar.h>

struct ICONDIRENTRY
{
	BYTE bWidth;
	BYTE bHeight;
	BYTE bColorCount;
	BYTE bReserved;
	WORD wPlanes;
	WORD wBitCount;
	DWORD dwBytesInRes;
	DWORD dwImageOffset;
};

// ICO文件的头部数据，共6字节。
// 可以看出来这种文件格式的设计方式就是为了
// 方便C/C++进行读取的，可以直接将文件反序列化到
// 结构体层面。
struct ICONDIR
{
	// 这个不知道是什么
	WORD idReserved;
	// Icon类型
	WORD idType;
	// Icon的图像个数
	WORD idCount;
};

#pragma pack(push, 2)
typedef struct {
	WORD Reserved1;       // reserved, must be 0
	WORD ResourceType;    // type is 1 for icons
	WORD ImageCount;      // number of icons in structure (1)
	BYTE Width;           // icon width (32)
	BYTE Height;          // icon height (32)
	BYTE Colors;          // colors (0 means more than 8 bits per pixel)
	BYTE Reserved2;       // reserved, must be 0
	WORD Planes;          // color planes
	WORD BitsPerPixel;    // bit depth
	DWORD ImageSize;      // size of structure
	WORD ResourceID;      // resource ID
} GROUPICON;

// 图像的头部数据格式
struct GRPICONDIRENTRY
{
	BYTE bWidth;
	BYTE bHeight;
	BYTE bColorCount;
	BYTE bReserved;
	WORD wPlanes;
	WORD wBitCount;
	DWORD dwBytesInRes;
	WORD nID;
};

struct GRPICONDIR
{
	WORD idReserved;
	WORD idType;
	WORD idCount;
	GRPICONDIRENTRY idEntries;
};



LPWSTR ConvertCharToLPWSTR(const char* szString)
{
	int dwLen = strlen(szString) + 1;
	int nwLen = MultiByteToWideChar(CP_ACP, 0, szString, dwLen, NULL, 0);//算出合适的长度
	LPWSTR lpszPath = new WCHAR[dwLen];
	MultiByteToWideChar(CP_ACP, 0, szString, dwLen, lpszPath, nwLen);
	return lpszPath;
}

JNIEXPORT jboolean JNICALL Java_org_swdc_plugin_PlatformNatives_addWindowsIcon
(JNIEnv* env, jclass clazz, jstring exePath, jstring iconPath) {

	jboolean False = 0;

	HANDLE hIconFile = CreateFile(
		ConvertCharToLPWSTR(env->GetStringUTFChars(iconPath,&False)),	// 文件路径
		GENERIC_READ,							// 打开方式 可以（通过“|”运算符）组合使用，读或者写
		FILE_SHARE_READ,									// 共享选项，NULL则独占此文件
		NULL,									// 安全性选项，留空即可
		OPEN_EXISTING,							// 创建选项，这里单纯打开文件不创建。
		FILE_ATTRIBUTE_NORMAL,					// 可以指定文件的属性
		NULL);

	if (hIconFile == INVALID_HANDLE_VALUE) {
		printf("无法打开图标文件 %s\n", env->GetStringUTFChars(iconPath, &False));
		return FALSE;
	}

	ICONDIR iconDir = { 0 };
	ICONDIRENTRY iconDirEntry = { 0 };
	GRPICONDIR iconGroupDir = { 0 };
	DWORD size, gSize, dwReserved;

	PBYTE pIcon, pGrpIcon;

	// 读取Icon的文件头部
	BOOL rst = ReadFile(hIconFile, &iconDir, sizeof(ICONDIR), &dwReserved, NULL);
	// 读取图像数据
	rst = ReadFile(hIconFile, &iconDirEntry, sizeof(GRPICONDIRENTRY), &dwReserved, NULL);

	if (!rst) {
		printf("无法打开图标");
		CloseHandle(hIconFile);
		return FALSE;
	}

	// 申请需要的内存
	size = iconDirEntry.dwBytesInRes;
	pIcon = (PBYTE)malloc(size);

	// 设置文件读取的位置
	SetFilePointer(hIconFile, iconDirEntry.dwImageOffset, NULL, FILE_BEGIN);
	// 读取文件内容
	rst = ReadFile(hIconFile, (LPVOID)pIcon, size, &dwReserved, NULL);

	if (!rst) {
		printf("无法读取图标");
		CloseHandle(hIconFile);
		return FALSE;
	}

	GROUPICON icon;

	// This is the header
	icon.Reserved1 = 0;     // reserved, must be 0
	icon.Reserved2 = 0;     // reserved, must be 0
	icon.ResourceType = 1;  // type is 1 for icons
	icon.ImageCount = 1;    // number of icons in structure (1)
	icon.Height = iconDirEntry.bHeight;
	icon.Width = iconDirEntry.bWidth;
	icon.ImageSize = iconDirEntry.dwBytesInRes;
	icon.Colors = iconDirEntry.bColorCount;
	icon.BitsPerPixel = iconDirEntry.wBitCount;
	icon.Planes = iconDirEntry.wPlanes;
	icon.ResourceID = 1;

	HANDLE hTarget = BeginUpdateResource(ConvertCharToLPWSTR(env->GetStringUTFChars(exePath, &False)),true);

	if (hTarget == INVALID_HANDLE_VALUE) {
		printf("无法找到目标文件");
		CloseHandle(hIconFile);
		return FALSE;
	}

	rst = UpdateResource(hTarget, RT_GROUP_ICON, L"MAINICON", NULL, (LPVOID)&icon, sizeof(GROUPICON));
	rst = UpdateResource(hTarget, RT_ICON, MAKEINTRESOURCE(1), NULL, (LPVOID)pIcon, size);

	EndUpdateResource(hTarget, false);

	if (!rst) {
		printf("无法写入图标");
		CloseHandle(hIconFile);
		return FALSE;
	}
	CloseHandle(hIconFile);
	return TRUE;
}