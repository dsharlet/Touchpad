#ifndef PROTOCOL_H
#define PROTOCOL_H

#include "Android.h"

// Protocol.
enum CONTROL
{
	// Control packets.
	C_CONNECT			= 0x00,
	C_DISCONNECT		= 0x01,
	C_PING				= 0x02,
	C_ACK				= 0x03,
	C_SUSPEND			= 0x04,
	C_RESUME			= 0x05,

	// Mouse packets.
	C_MOUSE_MOVE		= 0x11,
	C_MOUSE_BUTTONDOWN	= 0x12,
	C_MOUSE_BUTTONUP	= 0x13,
	C_MOUSE_SCROLL		= 0x16,
	C_MOUSE_SCROLL2		= 0x17,

	// Keyboard packets.
	C_CHAR				= 0x20,
	C_KEYPRESS			= 0x21,
	C_KEYDOWN			= 0x22,
	C_KEYUP				= 0x23,
	
	// Empty packet.
	C_NULL				= 0xFF,
};

#pragma pack(push, 1)
struct Packet
{
	unsigned char Control;
	union
	{
		int Password;
		char Delta;
		struct
		{
			char dx, dy;
		} Delta2D;
		wchar_t Char;
		struct
		{
			short keycode;	// ANDROID_KEYCODE
			short meta;
		} Key;
		char Button;
		char Reason;
		int Count;

		unsigned short Port;

		unsigned int _padding;
	};
};
#pragma pack(pop)

static_assert(sizeof(Packet) == 5, "sizeof(Packet) != 5");

#endif