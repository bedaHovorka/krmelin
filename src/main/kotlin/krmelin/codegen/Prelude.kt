package krmelin.codegen

/**
 * Emission-time name mappings (Plan.md §4.6).
 *
 * Type aliases (`Dryst`→`String`, `Cyslo`→`Int`, …) are deliberately NOT here: their
 * single source of truth is `KType.kotlinName`, defined in `krmelin.resolve.Prelude`.
 * The emitter resolves written type names through `Resolution.fileScope` and reads the
 * Kotlin name back. This object holds only the renames that have no `KType` home —
 * member/function spellings and the entry-point convention.
 *
 * Despite the file name (Plan.md §3 reserves `codegen/Prelude.kt` for the codegen
 * prelude), nothing here is emitted into the output as a companion file: §4.4 shows
 * generated code using Kotlin names directly. This is the semantic sibling, not a copy,
 * of `krmelin.resolve.Prelude` — keep both in step when dialect names change.
 */
object KotlinPrelude {
    /** `pravit(x)` → `print(x)`, `zarvat(x)` → `println(x)`. */
    val PRINT_FUNCTION_NAMES: Map<String, String> = mapOf("pravit" to "print", "zarvat" to "println")

    /** `.naDryst()` → `.toString()` (lowering, receiver permitting). */
    const val STRINGIFY_MEMBER = "naDryst"
    const val TO_STRING_MEMBER = "toString"

    /** `.dylka` → `.length` (Dryst) or `.size` (Halda/Kupa) depending on receiver type. */
    const val LENGTH_MEMBER = "dylka"
    const val LENGTH_PROPERTY = "length"
    const val SIZE_PROPERTY = "size"

    /** Top-level, parameterless `rynek` is the entry point, emitted as Kotlin `main`. */
    const val ENTRY_POINT_KRMELIN = "rynek"
    const val ENTRY_POINT_KOTLIN = "main"

    /** The Flakanci exception hierarchy (Plan.md §7), emitted by the runtime resource. */
    val FLAKANCI_TYPE_NAMES: Set<String> =
        setOf("Flakanec", "ChujovyFlakanec", "MimoBarak", "DelenoNulou", "ZlyDryst")

    const val RUNTIME_PACKAGE = "krmelin.runtime"
    const val RUNTIME_IMPORT_LINE = "import krmelin.runtime.*"
}
