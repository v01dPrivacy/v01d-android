# Tor VPN Bidirectional Forwarding Plan

## Problem
Current TorVpnService is one-way: outbound packets go through Tor SOCKS, but
responses are logged and dropped — never written back to the TUN interface.
Apps can't receive data → VPN is unusable for browsing.

## Solution
Implement a minimal userspace TCP stack that:
1. Tracks TCP connections (state per connection: seq/ack numbers, IPs, ports)
2. Handles TCP handshake (SYN → SYN-ACK → ACK)
3. Forwards app data through Tor SOCKS proxy
4. **Reconstructs response IP/TCP packets** and writes them back to TUN
5. Handles TCP teardown (FIN → FIN-ACK)

## Key Components

### TcpConnection (per-connection state)
- srcIp, dstIp, srcPort, dstPort
- mySeq (our sequence number for responses)
- theirSeq (app's sequence number, tracks ACKs)
- socket (SOCKS-proxied Socket to destination)
- state: SYN_RECEIVED, ESTABLISHED, CLOSING

### Packet Building
- `buildIpPacket(srcIp, dstIp, tcpPayload)` → full IP header + TCP header + payload
- IP checksum: ones-complement sum of IP header
- TCP checksum: pseudo-header (src IP, dst IP, protocol=6, TCP length) + TCP header + payload

### Packet Flow (bidirectional)
```
App → TUN → [parse IP/TCP]
  SYN?  → send SYN-ACK to TUN, connect SOCKS to dst
  DATA? → forward payload through SOCKS socket
  FIN?  → send FIN-ACK to TUN, close SOCKS

SOCKS response → [build IP/TCP packet] → TUN → App
```

## Files
- Rewrite `TorVpnService.kt` — add TcpConnection, packet building, bidirectional forwarding

## Limitations (documented)
- No TCP retransmission (relies on SOCKS socket reliability)
- No window scaling (fixed 65535 window)
- No TCP options (MSS, SACK, timestamps)
- UDP still dropped (DNS via Tor SOCKS for TCP connections)
