interface DeclarationDescriptor

interface ClassifierDescriptor : DeclarationDescriptor

interface ScriptDescriptor : ClassifierDescriptor

interface IrSymbol {
    val descriptor: DeclarationDescriptor
}

interface IrClassifierSymbol : IrSymbol {
    override val descriptor: ClassifierDescriptor
}

interface IrBindableSymbol<out D : DeclarationDescriptor> : IrSymbol {
    override val descriptor: D
}

interface IrScriptSymbol : IrClassifierSymbol, IrBindableSymbol<ScriptDescriptor>

abstract class IrSymbolBase<out D : DeclarationDescriptor>(
    private val _descriptor: D?
) : IrSymbol {
    override val descriptor: D
        get() = _descriptor!!
}

abstract class IrBindableSymbolBase<out D : DeclarationDescriptor>(
    descriptor: D?
) : IrBindableSymbol<D>, IrSymbolBase<D>(descriptor)

<!ABSTRACT_MEMBER_NOT_IMPLEMENTED!>class IrScriptSymbolImpl<!>(
    descriptor: ScriptDescriptor? = null
) : IrScriptSymbol, IrBindableSymbolBase<ScriptDescriptor>(descriptor)
