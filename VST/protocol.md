# LAN Audio Protocol (TamTalk v0)

This protocol is simple and optimized for LAN MVP.

## Transport

- UDP
- Default port: `50020`
- Audio format: PCM16 LE, mono, 48 kHz
- Frame size: 10 ms (480 samples = 960 bytes payload)

## Packet format

Each datagram:

- Bytes `[0..3]`: magic `TALK` (ASCII)
- Byte `[4]`: version (`1`)
- Byte `[5]`: message type
  - `1` = Audio frame
  - `2` = Keepalive
  - `3` = Client hello
- Bytes `[6..9]`: sequence (`uint32`, little-endian)
- Bytes `[10..17]`: timestamp micros (`uint64`, little-endian)
- Bytes `[18..19]`: client id length (`uint16`, little-endian)
- Bytes `[20..(20+len-1)]`: UTF-8 client id
- Remaining bytes: payload

For `message type = 1`, payload is PCM16 frame.

## Mix rules (host)

- Maintain per-client latest frame.
- On each output callback, sum active client samples with float conversion.
- Apply soft limiter/clamp to avoid clipping.
- Expire silent/disconnected clients if no frame for > 500 ms.

## Reliability model

- UDP unordered; host accepts best-effort real-time audio.
- Lost packets are ignored (no retransmit).
- Keepalive every 1 s from client.

## Security (LAN baseline)

- Restrict host bind to trusted LAN.
- Optionally add pre-shared key in hello payload.
- For internet/WAN usage, migrate to SRTP/WebRTC.
