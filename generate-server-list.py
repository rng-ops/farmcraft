#!/usr/bin/env python3
"""
Generate a Minecraft servers.dat NBT file to auto-populate multiplayer server list.
"""

import struct
import gzip
from pathlib import Path

class NBTWriter:
    """Simple NBT writer for Minecraft servers.dat."""
    
    def __init__(self):
        self.data = bytearray()
    
    def write_string(self, value: str) -> None:
        """Write UTF-8 string with length prefix."""
        encoded = value.encode('utf-8')
        self.data.extend(struct.pack('>H', len(encoded)))
        self.data.extend(encoded)
    
    def write_byte(self, value: int) -> None:
        """Write a single byte."""
        self.data.append(value & 0xFF)
    
    def write_int(self, value: int) -> None:
        """Write a 32-bit integer."""
        self.data.extend(struct.pack('>i', value))
    
    def write_tag_byte(self, name: str, value: int) -> None:
        """Write TAG_Byte with name."""
        self.write_byte(1)  # TAG_Byte
        self.write_string(name)
        self.data.extend(struct.pack('b', value))
    
    def write_tag_string(self, name: str, value: str) -> None:
        """Write TAG_String with name."""
        self.write_byte(8)  # TAG_String
        self.write_string(name)
        self.write_string(value)
    
    def write_tag_end(self) -> None:
        """Write TAG_End."""
        self.write_byte(0)
    
    def get_bytes(self) -> bytes:
        """Get the final byte array."""
        return bytes(self.data)

def create_servers_dat():
    """Create a servers.dat NBT file with FarmCraft dev server."""
    nbt = NBTWriter()
    
    # Root compound tag with empty name
    nbt.write_byte(10)  # TAG_Compound
    nbt.write_string("")  # Empty root name
    
    # TAG_List "servers"
    nbt.write_byte(9)  # TAG_List
    nbt.write_string("servers")
    nbt.write_byte(10)  # List element type: TAG_Compound
    nbt.write_int(1)  # List size: 1 server
    
    # Server compound (no tag header in list elements)
    nbt.write_tag_string("ip", "localhost:25565")
    nbt.write_tag_string("name", "FarmCraft Dev Server")
    nbt.write_tag_byte("hideAddress", 0)
    nbt.write_tag_end()  # End server compound
    
    # End root compound
    nbt.write_tag_end()
    
    return nbt.get_bytes()

def main():
    """Generate servers.dat file in the Minecraft run directory."""
    script_dir = Path(__file__).parent
    servers_file = script_dir / "mod/forge/run/servers.dat"
    
    print("Generating servers.dat...")
    
    # Create NBT data
    nbt_data = create_servers_dat()
    
    # Compress with gzip
    with gzip.open(servers_file, 'wb') as f:
        f.write(nbt_data)
    
    print(f"✓ Created {servers_file}")
    print("  Server: FarmCraft Dev Server")
    print("  Address: localhost:25565")

if __name__ == "__main__":
    main()
