#ifndef SERVER_H
#define SERVER_H

#include "Socket.h"
#include "Thread.h"
#include "Protocol.h"

// Default port to use.
const int DefaultPort = 2999;

// Log output level.
enum OUTPUT_LEVEL
{
	OL_ERROR = 1,
	OL_WARNING = 2,
	OL_INFO = 3,
	OL_VERBOSE = 4,

	OL_NOTIFY = 0x40000000,
	OL_STATUS = 0x80000000,
};

// Log message.
void Log(int level, const wchar_t * s, ...);

class Server : public ts::Thread
{
protected:
	short port;
	int password;

	ts::TcpSocket client, server;
	ts::UdpSocket beacons[2];

	void InitSockets();
	void HandlePackets();
	void AcceptClients();
	void CheckBeacon(int beacon);

	void Main(const volatile bool & run);

public:
	bool IsRunning();
	bool Run(short port, int password);
};

#endif