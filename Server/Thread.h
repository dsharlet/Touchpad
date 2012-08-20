#ifndef THREAD_H
#define THREAD_H

#include "Windows.h"

namespace ts
{
	class Thread
	{
	private:
		HANDLE thread;
		volatile bool run;
	
		static DWORD WINAPI ThreadProc(void * This);

	protected:	
		virtual void Main(const volatile bool & run) { }

	public:
		Thread();
		virtual ~Thread();

		virtual void Run();
		virtual void Stop();

		virtual bool IsRunning();
	};
}

#endif