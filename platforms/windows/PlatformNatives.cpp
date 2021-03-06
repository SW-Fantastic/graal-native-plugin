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

// ICO�ļ���ͷ�����ݣ���6�ֽڡ�
// ���Կ����������ļ���ʽ����Ʒ�ʽ����Ϊ��
// ����C/C++���ж�ȡ�ģ�����ֱ�ӽ��ļ������л���
// �ṹ����档
struct ICONDIR
{
	// �����֪����ʲô
	WORD idReserved;
	// Icon����
	WORD idType;
	// Icon��ͼ�����
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

// ͼ���ͷ�����ݸ�ʽ
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
	int nwLen = MultiByteToWideChar(CP_ACP, 0, szString, dwLen, NULL, 0);//������ʵĳ���
	LPWSTR lpszPath = new WCHAR[dwLen];
	MultiByteToWideChar(CP_ACP, 0, szString, dwLen, lpszPath, nwLen);
	return lpszPath;
}

JNIEXPORT jboolean JNICALL Java_org_swdc_plugin_PlatformNatives_addWindowsIcon
(JNIEnv* env, jclass clazz, jstring exePath, jstring iconPath) {

	jboolean False = 0;

	HANDLE hIconFile = CreateFile(
		ConvertCharToLPWSTR(env->GetStringUTFChars(iconPath,&False)),	// �ļ�·��
		GENERIC_READ,							// �򿪷�ʽ ���ԣ�ͨ����|������������ʹ�ã�������д
		FILE_SHARE_READ,									// ����ѡ�NULL���ռ���ļ�
		NULL,									// ��ȫ��ѡ����ռ���
		OPEN_EXISTING,							// ����ѡ����ﵥ�����ļ���������
		FILE_ATTRIBUTE_NORMAL,					// ����ָ���ļ�������
		NULL);

	if (hIconFile == INVALID_HANDLE_VALUE) {
		printf("�޷���ͼ���ļ� %s\n", env->GetStringUTFChars(iconPath, &False));
		return FALSE;
	}

	ICONDIR iconDir = { 0 };
	ICONDIRENTRY iconDirEntry = { 0 };
	GRPICONDIR iconGroupDir = { 0 };
	DWORD size, gSize, dwReserved;

	PBYTE pIcon, pGrpIcon;

	// ��ȡIcon���ļ�ͷ��
	BOOL rst = ReadFile(hIconFile, &iconDir, sizeof(ICONDIR), &dwReserved, NULL);
	// ��ȡͼ������
	rst = ReadFile(hIconFile, &iconDirEntry, sizeof(GRPICONDIRENTRY), &dwReserved, NULL);

	if (!rst) {
		printf("�޷���ͼ��");
		CloseHandle(hIconFile);
		return FALSE;
	}

	// ������Ҫ���ڴ�
	size = iconDirEntry.dwBytesInRes;
	pIcon = (PBYTE)malloc(size);

	// �����ļ���ȡ��λ��
	SetFilePointer(hIconFile, iconDirEntry.dwImageOffset, NULL, FILE_BEGIN);
	// ��ȡ�ļ�����
	rst = ReadFile(hIconFile, (LPVOID)pIcon, size, &dwReserved, NULL);

	if (!rst) {
		printf("�޷���ȡͼ��");
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
		printf("�޷��ҵ�Ŀ���ļ�");
		CloseHandle(hIconFile);
		return FALSE;
	}

	rst = UpdateResource(hTarget, RT_GROUP_ICON, L"MAINICON", NULL, (LPVOID)&icon, sizeof(GROUPICON));
	rst = UpdateResource(hTarget, RT_ICON, MAKEINTRESOURCE(1), NULL, (LPVOID)pIcon, size);

	EndUpdateResource(hTarget, false);

	if (!rst) {
		printf("�޷�д��ͼ��");
		CloseHandle(hIconFile);
		return FALSE;
	}
	CloseHandle(hIconFile);
	return TRUE;
}