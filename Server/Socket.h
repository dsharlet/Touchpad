#ifndef SOCKET_H
#define SOCKET_H

#include <cassert>
#include <stdexcept>
#include <string>

#include <WinSock2.h>
#include "Windows.h"

namespace ts
{
	// Socket exceptions.
	class socket_exception : public win_exception
	{
	public:
		socket_exception(const char * fn, int e = WSAGetLastError()) : win_exception(fn, e) { }
	};

	// Socket initialization.
	void InitSockets();
	void CloseSockets();

	// Socket address wrapper.
	class Address
	{
	protected:
		union
		{
			sockaddr addr;
			sockaddr_in in;
		};
		int size;

	public:
		Address(short port = 0);

		int * RefSize();
		sockaddr * RefSockAddr();
		const sockaddr * RefSockAddr() const;

		int Size() const;

		std::wstring ToString(bool port = true) const;

		bool operator == (const Address & r) const { return size == r.size && memcmp(&addr, &r.addr, size) == 0; }
		bool operator != (const Address & r) const { return size != r.size || memcmp(&addr, &r.addr, size) != 0; }

		static Address LocalHost(short port = 0);
	};
	
	// Base socket class.
	class Socket
	{
	private:
		Socket(const Socket & copy);
		void operator = (const Socket & assign);

	protected:
		SOCKET s;

		void IoCtlSocket(long cmd, u_long & mode);
		
	public:
		Socket() : s(INVALID_SOCKET) { }
		virtual ~Socket() { Close(); }
		
		void SetBlocking(bool blocking);

		void Close();
		
		bool IsValid() { return s != INVALID_SOCKET; }
	};
		
	// TCP socket wrapper.
	class TcpSocket : public Socket
	{
	public:
		// Connection.
		void Listen(const Address & addr, int queue = 1, bool blocking = true);
		bool Accept(TcpSocket & listener, Address & addr, bool blocking = true);

		// Data transfer.
		int Receive(void * buffer, int size, int timeout = 0);
		int Send(void * buffer, int size, int timeout = 0);
		
		// Get peer address.
		Address GetPeer();

		// Take ownership of 'other'.
		void Take(TcpSocket & other);
	};

	// UDP socket wrapper.
	class UdpSocket : public Socket
	{
	public:
		UdpSocket() { }

		// Bind socket to local address.
		void Bind(Address addr = Address(), bool blocking = true);

		// Data transfer.
		int ReceiveFrom(void * buffer, int size, Address & from, int timeout = 0);
		int SendTo(void * buffer, int size, const Address & to, int timeout = 0);
	};
}

#endif