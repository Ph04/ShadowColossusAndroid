// Diagnostic-only fallback table for clean CI builds without game-generated C++.
// Celosia's runtime references this table even when no game functions are linked.
// A zero-sized address range prevents the runtime from treating it as game code.
#include "ps2_runtime.h"

extern const uint32_t g_ps2RecompiledFunctionTableBase = 0x00000000u;
extern const uint32_t g_ps2RecompiledFunctionTableEnd = 0x00000000u;
extern const uint32_t g_ps2RecompiledFunctionTableSlotCount = 0u;
PS2Runtime::RecompiledFunction g_ps2RecompiledFunctionTable[1] = {};
