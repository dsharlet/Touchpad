#include "Thread.h"

#include <stdexcept>

namespace ts
{
	DWORD WINAPI Thread::ThreadProc(void * user)
	{
		Thread * This = (Thread *)user;
		try
		{
			This->Main(This->run);
			return 0;
		}
		catch(...)
		{
			return 1;
		}
	}

	Thread::Thread() : run(false), thread(NULL)
	{
	}

	Thread::~Thread()
	{
		Stop();
	}
	
	void Thread::Run()
	{
		Stop();

		run = true;
		thread = CreateThread(NULL, 0, ThreadProc, this, 0, NULL);
		if(!thread)
		{
			run = false;
			throw win_exception("CreateThread");
		}
	}

	void Thread::Stop()
	{
		if(thread)
		{
			run = false;
			WaitForSingleObject(thread, INFINITE);
			thread = NULL;
		}
	}

	bool Thread::IsRunning()
	{
		if(thread)
			return WaitForSingleObject(thread, 0) != WAIT_OBJECT_0;
		else
			return false;
	}
}