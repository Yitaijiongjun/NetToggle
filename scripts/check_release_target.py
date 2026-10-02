"""Check retained SIM enum and evaluate the selector in the actual packaged DEX.

This runs after R8/signing, unlike Gradle JVM tests. It evaluates only the tiny
pure selector method, not Android framework calls or telephone operations.
"""
import struct
import sys
from zipfile import ZipFile

TARGET = 'Lcom/dhangofa/networktoggle/model/TargetSim;'


class Dex:
    def __init__(self, data):
        self.data = data
        assert data[:4] == b'dex\n', 'Not a DEX file'
        self.strings = []
        for i in range(self.u32(0x38)):
            off = self.u32(self.u32(0x3c) + i * 4)
            _, off = self.uleb(off)
            end = data.index(b'\0', off)
            self.strings.append(data[off:end].decode('utf-8', errors='replace'))
        self.types = [self.strings[self.u32(self.u32(0x44) + i * 4)] for i in range(self.u32(0x40))]
        self.fields = []
        for i in range(self.u32(0x50)):
            cls, typ, name = struct.unpack_from('<HHI', data, self.u32(0x54) + i * 8)
            self.fields.append((self.types[cls], self.types[typ], self.strings[name]))
        self.methods = []
        for i in range(self.u32(0x58)):
            cls, proto, name = struct.unpack_from('<HHI', data, self.u32(0x5c) + i * 8)
            p = self.u32(0x4c) + proto * 12
            returns = self.types[self.u32(p + 4)]
            params = self.u32(p + 8)
            args = [] if not params else [self.types[self.u16(params + 4 + j * 2)] for j in range(self.u32(params))]
            self.methods.append((self.types[cls], self.strings[name], returns, args))

    def u16(self, off): return struct.unpack_from('<H', self.data, off)[0]
    def u32(self, off): return struct.unpack_from('<I', self.data, off)[0]

    def uleb(self, off):
        value = shift = 0
        while True:
            byte = self.data[off]
            off += 1
            value |= (byte & 0x7f) << shift
            if not byte & 0x80: return value, off
            shift += 7

    def definitions(self):
        for i in range(self.u32(0x60)):
            off = self.u32(0x64) + i * 32
            cls, flags, parent = struct.unpack_from('<III', self.data, off)
            yield self.types[cls], flags, self.types[parent], self.u32(off + 24)

    def code_for_selector(self):
        for _, _, _, off in self.definitions():
            if not off: continue
            counts = []
            for _ in range(4):
                size, off = self.uleb(off)
                counts.append(size)
            for _ in range(counts[0] + counts[1]):
                _, off = self.uleb(off)
                _, off = self.uleb(off)
            for size in counts[2:]:
                index = 0
                for _ in range(size):
                    diff, off = self.uleb(off)
                    flags, off = self.uleb(off)
                    code, off = self.uleb(off)
                    index += diff
                    _, name, returns, args = self.methods[index]
                    if name == 'targetForStep' and returns == TARGET and args == [TARGET, 'I']:
                        assert flags & 8 and code, 'Selector must be a concrete static method'
                        return code
        raise AssertionError('Packaged selector missing or its enum signature was rewritten')

    def select(self, code, target, step):
        registers, inputs = struct.unpack_from('<HH', self.data, code)
        assert inputs == 2
        words = struct.unpack_from('<' + 'H' * self.u32(code + 12), self.data, code + 16)
        values = [0] * registers
        values[registers - 2:] = [(TARGET, target), step]
        pc = 0
        signed = lambda value, bits: value - (1 << bits) if value & (1 << (bits - 1)) else value
        compare = (lambda a,b:a == b, lambda a,b:a != b, lambda a,b:a < b,
                   lambda a,b:a >= b, lambda a,b:a > b, lambda a,b:a <= b)
        for _ in range(100):
            word = words[pc]
            op, reg = word & 0xff, word >> 8
            if op in (0x01, 0x07):
                values[reg & 15] = values[reg >> 4]
                pc += 1
            elif op in (0x02, 0x08):
                values[reg] = values[words[pc + 1]]
                pc += 2
            elif op in (0x03, 0x09):
                values[words[pc + 1]] = values[words[pc + 2]]
                pc += 3
            elif op == 0x12:
                values[reg & 15] = signed(reg >> 4, 4)
                pc += 1
            elif op == 0x13:
                values[reg] = signed(words[pc + 1], 16)
                pc += 2
            elif op == 0x62: # sget-object: symbolic identity of the kept enum constant
                owner, typ, name = self.fields[words[pc + 1]]
                assert owner == typ == TARGET, 'Unexpected selector field'
                values[reg] = (TARGET, name)
                pc += 2
            elif 0x32 <= op <= 0x37:
                take = compare[op - 0x32](values[reg & 15], values[reg >> 4])
                pc += signed(words[pc + 1], 16) if take else 2
            elif 0x38 <= op <= 0x3d:
                take = compare[op - 0x38](values[reg], 0)
                pc += signed(words[pc + 1], 16) if take else 2
            elif op == 0x28:
                pc += signed(reg, 8)
            elif op == 0x29:
                pc += signed(words[pc + 1], 16)
            elif op == 0x11: # return-object
                return values[reg]
            elif op == 0:
                pc += 1
            else:
                raise AssertionError(f'Selector entered unexpected opcode 0x{op:02x} at {pc}')
        raise AssertionError('Selector did not return')


with ZipFile(sys.argv[1]) as archive:
    dexes = [Dex(archive.read(n)) for n in archive.namelist() if n.startswith('classes') and n.endswith('.dex')]
enum = [(dex, flags, parent) for dex in dexes for name,flags,parent,_ in dex.definitions() if name == TARGET]
assert len(enum) == 1 and enum[0][1] & 0x4000 and enum[0][2] == 'Ljava/lang/Enum;', 'SIM enum missing/unboxed in Release APK'
assert {'AUTO','SIM_1','SIM_2','BOTH'} <= {name for owner,typ,name in enum[0][0].fields if owner == typ == TARGET}
checked = False
for dex in dexes:
    if not any(name == 'targetForStep' for _,name,_,_ in dex.methods): continue
    code = dex.code_for_selector()
    for target, step, expected in [('AUTO',0,'AUTO'), ('SIM_1',0,'SIM_1'), ('SIM_2',0,'SIM_2'),
                                    ('BOTH',0,'SIM_1'), ('BOTH',1,'SIM_2')]:
        actual = dex.select(code, target, step)
        assert actual == (TARGET, expected), (target, step, actual)
    checked = True
assert checked, 'No packaged selector checked'
print('Release DEX checks passed: real SIM enum retained; AUTO/SIM_1/SIM_2/BOTH selector paths verified in APK.')
