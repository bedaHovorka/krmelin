package krmelin.resolve

import krmelin.ast.Decl
import krmelin.ast.Expr
import krmelin.ast.FunBody
import krmelin.ast.LambdaBody
import krmelin.ast.Stmt
import krmelin.ast.TemplatePart
import krmelin.ast.TypeNode
import krmelin.diag.DiagCode
import krmelin.diag.DiagnosticReporter
import krmelin.lexer.SourceSpan
import krmelin.types.KType

/**
 * Binds names to declarations (Plan.md §5).
 *
 * Each scope level is processed in two passes — declare everything, then bind bodies — so
 * forward references between top-level declarations and between class members resolve just
 * like in Kotlin. Undeclared names are reported (HAV220, with a did-you-mean hint) but never
 * abort the walk: the type checker treats an unbound name as [KType.UNKNOWN] and stays quiet.
 */
class Resolver(private val reporter: DiagnosticReporter) {
    private val table = SymbolTable(Prelude.scope)
    private lateinit var resolution: Resolution

    /** One resolution per type node — the declare and bind passes revisit the same nodes. */
    private val typeCache = java.util.IdentityHashMap<TypeNode, KType?>()

    /** Each class's scope, built in the declare pass and reused by the bind pass so class-member
     *  bindings resolve to the same symbols that [resolution.declarations] / [Symbol.TypeName.members]
     *  hold — otherwise an inferred member type written via `declarations` would be invisible to
     *  inference reading `bindings`. */
    private val classScopes = java.util.IdentityHashMap<Decl.ClassDecl, Scope>()

    /**
     * Set by a `privezt …*` import. The compiler carries no model of the Kotlin stdlib, so
     * once a wildcard is in play it cannot tell a typo from a legitimately imported name;
     * reporting HAV220 would be a false positive, so it stops and lets `kotlinc` judge.
     */
    private var hasWildcardImport = false

    fun resolve(unit: Decl.CompilationUnit): Resolution {
        val fileScope = table.newFileScope()
        resolution = Resolution(fileScope)
        validateAnnotations(unit)
        declareAll(unit.declarations, fileScope)
        // After the declarations, so a local `robota abs` wins over `privezt kotlin.math.abs`.
        declareImports(unit.imports, fileScope)
        for (decl in unit.declarations) bindDecl(decl, fileScope)
        return resolution
    }

    // ── PorubaUnit annotations (Plan.md §6) ──────────────────────────────────

    /**
     * Reports misplaced `@Sichta` / `@Parta` in source order, before the declare/bind
     * passes run — a misplaced annotation fails loudly rather than being silently dropped
     * from the test registry.
     */
    private fun validateAnnotations(unit: Decl.CompilationUnit) {
        for (decl in unit.declarations) validateAnnotations(decl, topLevel = true)
    }

    private fun validateAnnotations(decl: Decl, topLevel: Boolean = false) {
        when (decl) {
            is Decl.PropertyDecl -> {
                if (decl.annotations.contains("Sichta")) {
                    reporter.error(
                        DiagCode.SICHTA_NOT_ON_ROBOTA,
                        "@Sichta slusi enem robote, zadny vlastnosti",
                        decl.span,
                        highlight = "@Sichta na vlastnosti nema vyznam",
                        fix = "ubal to do roboty: '@Sichta robota ${decl.name}_sichta() { ... }'",
                    )
                }
                if (decl.annotations.contains("Parta")) reportPartaMisplaced(decl.span, "vlastnost")
            }
            is Decl.FunDecl -> {
                if (decl.isTest) validateSichtaFun(decl, topLevel)
                if (decl.annotations.contains("Parta")) reportPartaMisplaced(decl.span, "robota")
            }
            is Decl.ClassDecl -> {
                if (decl.isParta && (decl.isObject || decl.isInterface)) {
                    reportPartaMisplaced(decl.span, if (decl.isObject) "jedynak" else "predpis")
                }
                val memberTests = decl.members.filterIsInstance<Decl.FunDecl>().filter { it.isTest }
                if (decl.isParta && memberTests.isNotEmpty() &&
                    decl.params.any { it.defaultValue == null }
                ) {
                    reporter.error(
                        DiagCode.PARTA_CTOR_PARAM_REQUIRED,
                        "parta '${decl.name}' so sichtami se musi zalozit bez argumentu",
                        decl.span,
                        highlight = "konstruktor tu bere povinny parametr",
                        fix = "dej parametrum vychozi hodnoty — PorubaUnit zaklada party jako ${decl.name}()",
                    )
                }
                if (!decl.isParta) {
                    for (test in memberTests) {
                        reporter.warning(
                            DiagCode.SICHTA_OUTSIDE_PARTA,
                            "sichta '${test.name}' v ${decl.name} bez @Parta se nikdy nespusti",
                            test.span,
                            highlight = "tuhle robotu PorubaUnit nenajde",
                            fix = "dej @Parta na trydu '${decl.name}', abo vytahni sichtu na uroven suboru",
                        )
                    }
                }
                for (member in decl.members) validateAnnotations(member, topLevel = false)
            }
            else -> Unit
        }
    }

    private fun reportPartaMisplaced(span: SourceSpan, co: String) {
        reporter.error(
            DiagCode.PARTA_NOT_ON_TRYDA,
            "@Parta slusi enem tryde, zadny $co",
            span,
            highlight = "@Parta tu nema vyznam",
            fix = "dej @Parta na trydu, ktera seskupuje @Sichta roboty",
        )
    }

    private fun validateSichtaFun(decl: Decl.FunDecl, topLevel: Boolean) {
        // `rynek` is the program entry point only as a top-level function; a member named
        // `rynek` is an ordinary method (Prelude.isEntryPoint is consulted for top-level decls
        // only), so a `@Parta` member test named `rynek` is fine and must not trip HAV232.
        if (topLevel && decl.name == "rynek") {
            reporter.error(
                DiagCode.SICHTA_ON_RYNEK,
                "rynek je zavedec programu, zadna sichta z neho nebude",
                decl.span,
                highlight = "rynek zadnou anotaci nechce",
                fix = "odeber @Sichta — abo prejmenuj roboutu, kaj ma byt test",
            )
            return
        }
        if (decl.params.isNotEmpty() || decl.body == null) {
            val reason =
                if (decl.body == null) "nema telo" else "ma parametry"
            reporter.error(
                DiagCode.SICHTA_WITH_PARAMS,
                "sichta '${decl.name}' se neda spustit: $reason",
                decl.span,
                highlight = "takovy robote PorubaUnit nezavola",
                fix = "testova robota musi byt bez parametru a s telem: '@Sichta robota ${decl.name}() { ... }'",
            )
        }
    }

    // ── Declaration pass ─────────────────────────────────────────────────────

    private fun declareImports(imports: List<Decl.ImportDecl>, scope: Scope) {
        for (import in imports) {
            if (import.wildcard) {
                hasWildcardImport = true
                continue
            }
            val name = import.name.lastOrNull() ?: continue
            // Opaque on purpose: there is no signature for an imported name here, so it types
            // as UNKNOWN and every use of it defers to kotlinc rather than false-positiving.
            scope.declare(Symbol.Variable(name, import.span, isMutable = false, type = KType.UNKNOWN))
        }
    }

    private fun declareAll(decls: List<Decl>, scope: Scope) {
        // Reported here, in source order, because the passes below visit classes out of order
        // and would otherwise blame whichever declaration they happened to reach first.
        reportDuplicates(decls)
        // Pass 1 — every class name becomes visible before any signature is resolved, so a
        // class can name itself (recursive types) and any class declared later in the file.
        val classes = decls.filterIsInstance<Decl.ClassDecl>().map { it to declareClassName(it, scope) }
        // Pass 2 — member signatures, now that every class name resolves.
        for ((decl, symbol) in classes) declareClassMembers(decl, symbol, scope)
        // Pass 3 — functions and properties.
        for (decl in decls) when (decl) {
            is Decl.FunDecl -> declareHoisted(functionSymbol(decl, scope), scope)
            is Decl.PropertyDecl -> declareHoisted(variableSymbol(decl, scope), scope)
            else -> Unit
        }
    }

    /** Names of the declarations that share one hoisted scope, in the order they were written. */
    private fun declaredName(decl: Decl): String? = when (decl) {
        is Decl.ClassDecl -> decl.name
        is Decl.FunDecl -> decl.name
        is Decl.PropertyDecl -> decl.name
        else -> null
    }

    private fun reportDuplicates(decls: List<Decl>) {
        val firstSpan = hashMapOf<String, SourceSpan>()
        for (decl in decls) {
            val name = declaredName(decl) ?: continue
            val earlier = firstSpan[name]
            if (earlier == null) firstSpan[name] = decl.span else reportDuplicate(name, decl.span, earlier)
        }
    }

    private fun declareClassName(decl: Decl.ClassDecl, scope: Scope): Symbol.TypeName {
        classScopes[decl] = Scope(parent = scope, kind = Scope.Kind.CLASS)
        val symbol = Symbol.TypeName(
            decl.name, decl.span,
            // declId keeps this distinct from a prelude alias that emits the same Kotlin name.
            KType(decl.name, decl.name, kind = KType.Kind.DECLARED, declId = decl.name),
            decl = decl,
        )
        declareHoisted(symbol, scope)
        return symbol
    }

    private fun declareClassMembers(decl: Decl.ClassDecl, symbol: Symbol.TypeName, scope: Scope) {
        val classScope = classScopes.getValue(decl)
        val members = linkedMapOf<String, Symbol>()
        fun addMember(member: Symbol) {
            recordDeclaration(member)
            val existing = members.putIfAbsent(member.name, member) ?: classScope.declare(member)
            if (existing != null) reportDuplicate(member.name, member.span, existing.span)
        }
        // Constructor params double as immutable/mutable members (zapisnik/tryda). Their types
        // resolve against the enclosing scope, which now holds every class name — this one too.
        val params = decl.params.map { paramSymbol(it, scope) }
        params.forEach(::addMember)
        for (member in decl.members) when (member) {
            is Decl.FunDecl -> addMember(functionSymbol(member, classScope))
            is Decl.PropertyDecl -> addMember(variableSymbol(member, classScope))
            else -> Unit
        }
        symbol.members = members
        // `predpis` is never constructed and `jedynak` already exists; everything else is
        // callable by name, and that call is what gives HAV350 something to check.
        if (!decl.isInterface && !decl.isObject) {
            symbol.constructor = Symbol.Function(
                decl.name, decl.span,
                params = params.map { ParamSig(it.name, it.type, it.hasDefault) },
                returnType = symbol.type,
            )
        }
    }

    private fun functionSymbol(decl: Decl.FunDecl, scope: Scope) = Symbol.Function(
        decl.name, decl.span,
        params = decl.params.map { ParamSig(it.name, typeFor(it.type, scope) ?: KType.UNKNOWN, it.defaultValue != null) },
        returnType = declaredType(decl.returnType, scope),
        decl = decl,
    )

    private fun variableSymbol(decl: Decl.PropertyDecl, scope: Scope) = Symbol.Variable(
        decl.name, decl.span,
        isMutable = decl.isMutable,
        type = declaredType(decl.type, scope),
        decl = decl,
    )

    private fun paramSymbol(param: Decl.Param, scope: Scope) = Symbol.Parameter(
        param.name, param.span,
        isMutable = param.isMutable,
        type = typeFor(param.type, scope) ?: KType.UNKNOWN,
        hasDefault = param.defaultValue != null,
        decl = param,
    )

    /**
     * A type that was written but could not be resolved becomes [KType.UNKNOWN], never `null`.
     * `null` means "nothing was written at all", and conflating the two makes the checker
     * contradict the resolver — it reads an unresolvable return type as "returns nothing" and
     * then objects to the `davaj` the user correctly wrote.
     */
    private fun declaredType(node: TypeNode?, scope: Scope): KType? =
        node?.let { typeFor(it, scope) ?: KType.UNKNOWN }

    private fun recordDeclaration(symbol: Symbol) {
        val declNode = when (symbol) {
            is Symbol.Variable -> symbol.decl
            is Symbol.Parameter -> symbol.decl
            is Symbol.Function -> symbol.decl
            is Symbol.TypeName -> symbol.decl
        }
        if (declNode != null) resolution.declarations[declNode] = symbol
    }

    /**
     * Declares a hoisted (file- or class-level) symbol. Collisions stay silent here because
     * [reportDuplicates] already reported them against the right declaration.
     */
    private fun declareHoisted(symbol: Symbol, scope: Scope) {
        recordDeclaration(symbol)
        if (scope.declare(symbol) == null) warnShadowing(symbol, scope)
    }

    private fun declare(symbol: Symbol, scope: Scope) {
        recordDeclaration(symbol)
        val existing = scope.declare(symbol)
        if (existing != null) reportDuplicate(symbol.name, symbol.span, existing.span)
        else warnShadowing(symbol, scope)
    }

    private fun reportDuplicate(name: String, span: SourceSpan, first: SourceSpan?) {
        reporter.error(
            DiagCode.DUPLICATE_DECLARATION,
            "'$name' je tu deklarovany podruhy",
            span,
            highlight = "tohle jmeno tu uz je",
            fix = first?.let { "prejmenuj jedno z nich; prvni deklarace: ${it.startLine}:${it.startCol}" },
            flourish = "dva krale na jednym trunu nesedza",
        )
    }

    private fun warnShadowing(symbol: Symbol, scope: Scope) {
        val owner = scope.parent?.scopeDeclaring(symbol.name) ?: return
        val outer = owner.lookupLocal(symbol.name) ?: return
        if (outer.span == SourceSpan.NONE) return // shadowing a prelude name is fine
        // A parameter or local named after a class member is the idiomatic setter/wither
        // shape; Kotlin does not warn about it either, and the member stays reachable.
        if (owner.kind == Scope.Kind.CLASS) return
        reporter.warning(
            DiagCode.SHADOWED_DECLARATION,
            "'${symbol.name}' zastira vnejsi deklaraci z ${outer.span.startLine}:${outer.span.startCol}",
            symbol.span,
            highlight = "tohle jmeno uz vnejsi deklarace ma",
            fix = "prejmenuj vnitrni '${symbol.name}', at je jasne, co je co",
        )
    }

    // ── Type names ──────────────────────────────────────────────────────────

    fun typeFor(node: TypeNode, scope: Scope): KType? =
        if (typeCache.containsKey(node)) typeCache[node] else resolveType(node, scope)
            .also { typeCache[node] = it }

    private fun resolveType(node: TypeNode, scope: Scope): KType? = when (node) {
        is TypeNode.NamedType -> {
            val symbol = scope.lookup(node.name)
            if (symbol !is Symbol.TypeName) {
                reporter.error(
                    DiagCode.UNKNOWN_TYPE_NAME,
                    "typ '${node.name}' neni deklarovany",
                    node.span,
                    highlight = "takovy typ tu neni",
                    fix = "myslel si treba 'Dryst', 'Cyslo' abo 'Bul'?",
                )
                null
            } else {
                val args = node.typeArgs.mapNotNull { typeFor(it, scope) }
                when {
                    args.size != node.typeArgs.size -> null
                    // Only a written-out argument list is checked; a bare `Halda` stays legal
                    // and is left to kotlinc, so this can never reject valid source.
                    node.typeArgs.isNotEmpty() && node.typeArgs.size != symbol.typeArity -> {
                        reportTypeArity(node, symbol)
                        // Recover with the bare type: one bad annotation should not cascade
                        // into a second, contradictory error further down the pipeline.
                        symbol.type.copy(nullable = node.nullable)
                    }
                    else -> symbol.type.copy(nullable = node.nullable, typeArgs = args)
                }
            }
        }
    }

    private fun reportTypeArity(node: TypeNode.NamedType, symbol: Symbol.TypeName) {
        reporter.error(
            DiagCode.TYPE_ARITY_MISMATCH,
            "'${node.name}' bere ${typeArgWord(symbol.typeArity)}, dal si ${node.typeArgs.size}",
            node.span,
            highlight = "spatny pocet typovych argumentu",
            fix = if (symbol.typeArity == 0) {
                "'${node.name}' zadne typove argumenty nebere — zrus '<...>'"
            } else {
                "'${node.name}' bere ${typeArgWord(symbol.typeArity)} — uprav '<...>'"
            },
        )
    }

    private fun typeArgWord(n: Int): String = when (n) {
        1 -> "1 typovy argument"
        2, 3, 4 -> "$n typove argumenty"
        else -> "$n typovych argumentu"
    }

    // ── Binding pass ─────────────────────────────────────────────────────────

    private fun bindDecl(decl: Decl, scope: Scope) {
        when (decl) {
            is Decl.ClassDecl -> {
                // Reuse the scope the declare pass built, so bindings resolve to the same
                // symbols that [resolution.declarations] / [Symbol.TypeName.members] hold.
                val classScope = classScopes.getValue(decl)
                // Constructor-parameter defaults are code too — without this their names never
                // resolve and an undeclared one survives into the emitted Kotlin.
                for (param in decl.params) param.defaultValue?.let { bindExpr(it, classScope) }
                for (member in decl.members) bindDecl(member, classScope)
            }
            is Decl.FunDecl -> bindFunction(decl, scope)
            is Decl.PropertyDecl -> decl.initializer?.let { bindExpr(it, scope) }
            else -> Unit
        }
    }

    private fun bindFunction(decl: Decl.FunDecl, scope: Scope) {
        val funScope = Scope(parent = scope, kind = Scope.Kind.FUNCTION)
        for (param in decl.params) {
            declare(paramSymbol(param, scope), funScope)
            param.defaultValue?.let { bindExpr(it, funScope) }
        }
        when (val body = decl.body) {
            is FunBody.BlockBody -> bindBlock(body.block, funScope)
            is FunBody.ExprBody -> bindExpr(body.expr, funScope)
            null -> Unit // abstract — nothing to bind
        }
    }

    private fun bindBlock(block: Stmt.Block, scope: Scope) {
        val blockScope = Scope(parent = scope, kind = Scope.Kind.BLOCK)
        // Locals are visible only after their declaration — declare as we go, statement
        // by statement, rather than hoisting the whole block.
        for (stmt in block.statements) bindStmt(stmt, blockScope)
    }

    private fun bindStmt(stmt: Stmt, scope: Scope) {
        when (stmt) {
            is Stmt.PropertyStmt -> {
                stmt.decl.initializer?.let { bindExpr(it, scope) }
                declare(variableSymbol(stmt.decl, scope), scope)
            }
            is Stmt.IfStmt -> {
                bindExpr(stmt.condition, scope)
                bindBlock(stmt.thenBlock, scope)
                for (elseIf in stmt.elseIfs) {
                    bindExpr(elseIf.condition, scope)
                    bindBlock(elseIf.block, scope)
                }
                stmt.elseBlock?.let { bindBlock(it, scope) }
            }
            is Stmt.WhenStmt -> {
                stmt.subject?.let { bindExpr(it, scope) }
                for (branch in stmt.branches) {
                    branch.conditions.forEach { bindExpr(it, scope) }
                    when (val body = branch.body) {
                        is krmelin.ast.WhenBody.ExprBody -> bindExpr(body.expr, scope)
                        is krmelin.ast.WhenBody.BlockBody -> bindBlock(body.block, scope)
                    }
                }
            }
            is Stmt.ForStmt -> {
                bindExpr(stmt.iterable, scope)
                val loopScope = Scope(parent = scope, kind = Scope.Kind.BLOCK)
                declare(
                    Symbol.Variable(stmt.name, stmt.span, isMutable = false, type = KType.UNKNOWN),
                    loopScope,
                )
                bindBlock(stmt.body, loopScope)
            }
            is Stmt.WhileStmt -> {
                bindExpr(stmt.condition, scope)
                bindBlock(stmt.body, scope)
            }
            is Stmt.ReturnStmt -> stmt.value?.let { bindExpr(it, scope) }
            is Stmt.TryStmt -> {
                bindBlock(stmt.block, scope)
                for (catch in stmt.catches) {
                    val catchScope = Scope(parent = scope, kind = Scope.Kind.BLOCK)
                    declare(paramSymbol(catch.param, scope), catchScope)
                    bindBlock(catch.block, catchScope)
                }
                stmt.finallyBlock?.let { bindBlock(it, scope) }
            }
            is Stmt.ThrowStmt -> bindExpr(stmt.expr, scope)
            is Stmt.ExprStmt -> bindExpr(stmt.expr, scope)
            is Stmt.BreakStmt, is Stmt.ContinueStmt -> Unit
            is Stmt.Block -> bindBlock(stmt, scope)
        }
    }

    private fun bindExpr(expr: Expr, scope: Scope) {
        when (expr) {
            is Expr.NameExpr -> bindName(expr, scope)
            is Expr.BinaryExpr -> { bindExpr(expr.left, scope); bindExpr(expr.right, scope) }
            is Expr.UnaryExpr -> bindExpr(expr.operand, scope)
            is Expr.CallExpr -> {
                bindExpr(expr.callee, scope)
                expr.args.forEach { bindExpr(it, scope) }
            }
            is Expr.MemberExpr -> bindExpr(expr.receiver, scope)
            is Expr.SafeMemberExpr -> bindExpr(expr.receiver, scope)
            is Expr.ElvisExpr -> { bindExpr(expr.left, scope); bindExpr(expr.right, scope) }
            is Expr.AssignExpr -> { bindExpr(expr.target, scope); bindExpr(expr.value, scope) }
            is Expr.ParenExpr -> bindExpr(expr.expr, scope)
            is Expr.StringTemplate -> expr.parts.forEach { part ->
                if (part is TemplatePart.Interpolation) bindExpr(part.expr, scope)
            }
            is Expr.LambdaExpr -> {
                val lambdaScope = Scope(parent = scope, kind = Scope.Kind.BLOCK)
                for (param in expr.params) {
                    declare(
                        Symbol.Variable(param, expr.span, isMutable = false, type = KType.UNKNOWN),
                        lambdaScope,
                    )
                }
                when (val body = expr.body) {
                    is LambdaBody.ExprBody -> bindExpr(body.expr, lambdaScope)
                    is LambdaBody.BlockBody -> bindBlock(body.block, lambdaScope)
                }
            }
            is Expr.IntLit, is Expr.FloatLit, is Expr.StringLit,
            is Expr.BoolLit, is Expr.NullLit -> Unit
        }
    }

    private fun bindName(expr: Expr.NameExpr, scope: Scope) {
        val symbol = scope.lookup(expr.name)
        if (symbol != null) {
            resolution.bindings[expr] = symbol
            return
        }
        // A wildcard import could legitimately supply this name — see [hasWildcardImport].
        if (hasWildcardImport) return
        val suggestion = table.suggest(expr.name, scope)
        reporter.error(
            DiagCode.UNDECLARED_NAME,
            "jmeno '${expr.name}' neni deklarovane",
            expr.span,
            highlight = "takove jmeno tu neni",
            fix = if (suggestion != null) {
                "nemyslel si '$suggestion'? velka pismena hraju roli"
            } else {
                "deklaruj '${expr.name}' driv, nez ho pouzijes"
            },
        )
    }
}
