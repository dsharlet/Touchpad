#include "Windows.h"

namespace ts
{
	win_exception::win_exception(const char * fn, int e) : std::runtime_error(fn), e(e) 
	{
		char buffer[1024];
		FormatMessageA(FORMAT_MESSAGE_FROM_SYSTEM, NULL, e, 0, buffer, sizeof(buffer) / sizeof(buffer[0]), NULL);

		w = fn;
		w += " Failed: ";
		w += buffer;
	}
	
	__int64 Time()
	{
		__int64 t;
		QueryPerformanceCounter((LARGE_INTEGER *)&t);
		return t;
	}

	__int64 Frequency()
	{
		__int64 f;
		QueryPerformanceFrequency((LARGE_INTEGER *)&f);
		return f;
	}
}