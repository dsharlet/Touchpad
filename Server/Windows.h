#ifndef WINDOWS_H
#define WINDOWS_H

#define WIN32_LEAN_AND_MEAN

#include <Windows.h>

#undef min
#undef max

#include <stdexcept>

namespace ts
{
	// Windows error code exception.
	class win_exception : public std::runtime_error
	{
	protected:
		int e;
		std::string w;

	public:
		win_exception(const char * fn, int e = GetLastError());

		const char * what() const { return w.c_str(); }
		int error() const { return e; }
	};

	__int64 Time();
	__int64 Frequency();
}

#endif