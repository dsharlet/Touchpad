#include "Socket.h"

namespace ts
{
	template < int N >
	void AddressToString(wchar_t (&buffer)[N], const Address & addr, bool port);

	bool ThrowSocketException(const char * fn, int err = WSAGetLastError());
	
	// Initialize Winsock.
	void InitSockets()
	{
		WSADATA wsa;
		int err = WSAStartup(0x0202, &wsa);
		if(err)
			throw socket_exception("WSAStartup", err);
	}

	void CloseSockets()
	{
		WSACleanup();
	}

	// Address
	Address::Address(short port) : size(sizeof(addr)) 
	{ 
		in.sin_family = AF_INET;
		in.sin_addr.s_addr = htonl(INADDR_ANY);
		in.sin_port = htons(port);
	}

	int * Address::RefSize() { size = sizeof(addr); return &size; }
	sockaddr * Address::RefSockAddr()  {  return &addr; }
	const sockaddr * Address::RefSockAddr() const { return &addr; }
	int Address::Size() const { return size; }
	
	std::wstring Address::ToString(bool port) const
	{
		wchar_t buffer[256];
		AddressToString(buffer, *this, port);
		return buffer;
	}

	Address Address::LocalHost(short port)
	{
		char name[256];
		if(gethostname(name, 256) == SOCKET_ERROR)
			throw socket_exception("gethostname");
		hostent* host = gethostbyname(name);    
		if(!host)
			throw socket_exception("gethostbyname");

		Address addr;
		memcpy(&addr.in.sin_addr, host->h_addr, host->h_length);
		addr.in.sin_port = htons(port);

		return addr;
	}

	// Socket
	void Socket::IoCtlSocket(long cmd, u_long & mode)
	{
		int err = ioctlsocket(s, cmd, &mode);
		if(err != NO_ERROR)
			throw socket_exception("Socket::IoCtlSocket", err);
	}

	void Socket::SetBlocking(bool blocking)
	{
		u_long mode;
		if(blocking) mode = 0;
		else mode = 1;
		IoCtlSocket(FIONBIO, mode);
	}
	
	void Socket::Close()
	{ 
		closesocket(s); 
		s = INVALID_SOCKET; 
	}

	// TcpSocket
	void TcpSocket::Listen(const Address & addr, int queue, bool blocking)
	{
		assert(!IsValid());

		try
		{
			s = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
			if(s == INVALID_SOCKET)
				throw socket_exception("TcpSocket::Listen");
			if(bind(s, addr.RefSockAddr(), addr.Size()) == SOCKET_ERROR)
				throw socket_exception("TcpSocket::Listen");
		
			if(!blocking)
				SetBlocking(false);

			int err = listen(s, queue);
			if(err == SOCKET_ERROR)
				throw socket_exception("TcpSocket::Listen");
		}
		catch(...)
		{
			Close();
			throw;
		}
	}

	bool TcpSocket::Accept(TcpSocket & l, Address & addr, bool blocking)
	{
		assert(!IsValid());

		try
		{
			s = accept(l.s, addr.RefSockAddr(), addr.RefSize());
			if(s == INVALID_SOCKET)
				return ThrowSocketException("TcpSocket::Accept");

			if(!blocking)
				SetBlocking(false);

			return true;
		}
		catch(...)
		{
			Close();
			throw;
		}
	}
	
	int TcpSocket::Receive(void * buffer, int size, int timeout)
	{
		if(timeout > 0)
		{
			fd_set set;
			FD_ZERO(&set);
			FD_SET(s, &set);
			timeval t = { 0, timeout * 1000 };
			select(1, &set, NULL, NULL, &t);
		}		

		int result = recv(s, (char *)buffer, size, 0);
		if(result == SOCKET_ERROR)
		{
			ThrowSocketException("TcpSocket::Receive");
			return 0;
		}
		return result;
	}
	int TcpSocket::Send(void * buffer, int size, int timeout)
	{
		if(timeout > 0)
		{
			fd_set set;
			FD_ZERO(&set);
			FD_SET(s, &set);
			timeval t = { 0, timeout * 1000 };
			select(1, NULL, &set, NULL, &t);
		}		

		int result = send(s, (char *)buffer, size, 0);
		if(result == SOCKET_ERROR)
			throw socket_exception("TcpSocket::Send");
		return result;
	}

	Address TcpSocket::GetPeer()
	{
		Address addr;
		int err = getpeername(s, addr.RefSockAddr(), addr.RefSize());
		if(err == SOCKET_ERROR)
			throw socket_exception("TcpSocket::GetPeer");
		return addr;
	}

	void TcpSocket::Take(TcpSocket & other)
	{
		assert(!IsValid());

		s = other.s;
		other.s = INVALID_SOCKET;
	}

	// UdpSocket
	void UdpSocket::Bind(Address addr, bool blocking)
	{
		assert(s == INVALID_SOCKET);
			
		try
		{
			s = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
			if(s == INVALID_SOCKET)
				throw socket_exception("UdpSocket::Bind");
			if(bind(s, addr.RefSockAddr(), addr.Size()) == SOCKET_ERROR)
				throw socket_exception("UdpSocket::Bind");

			if(!blocking)
				SetBlocking(false);
		}
		catch(...)
		{
			Close();
			throw;
		}
	}

	int UdpSocket::ReceiveFrom(void * buffer, int size, Address & from, int timeout)
	{
		if(timeout > 0)
		{
			fd_set set;
			FD_ZERO(&set);
			FD_SET(s, &set);
			timeval t = { 0, timeout * 1000 };
			select(1, &set, NULL, NULL, &t);
		}		

		int result = recvfrom(s, (char *)buffer, size, 0, from.RefSockAddr(), from.RefSize());
		if(result == SOCKET_ERROR)
		{
			ThrowSocketException("UdpSocket::ReceiveFrom");
			return 0;
		}
		return result;
	}

	int UdpSocket::SendTo(void * buffer, int size, const Address & to, int timeout)
	{
		if(timeout > 0)
		{
			fd_set set;
			FD_ZERO(&set);
			FD_SET(s, &set);
			timeval t = { 0, timeout * 1000 };
			select(1, NULL, &set, NULL, &t);
		}		

		int result = sendto(s, (char *)buffer, size, 0, to.RefSockAddr(), to.Size());
		if(result == SOCKET_ERROR)
			throw socket_exception("UdpSOcket::SendTo");
		return result;
	}

	// Get address
	template < int N >
	void AddressToString(wchar_t (&buffer)[N], const Address & addr, bool port)
	{
		DWORD length = N;
		if(WSAAddressToString(const_cast < sockaddr * > (addr.RefSockAddr()), addr.Size(), NULL, buffer, &length) != NO_ERROR)
		{
			wcscpy_s(buffer, L"<error>");
			return;
		}
		else
		{
			if(!port)
			{
				wchar_t * colon = wcschr(buffer, ':');
				if(colon)
					*colon = 0;
			}
		}
	}

	bool ThrowSocketException(const char * fn, int err)
	{
		if(err == NO_ERROR)
			return true;
		if(err == WSAEWOULDBLOCK)
			return false;
		throw socket_exception(fn, err);
	}
}