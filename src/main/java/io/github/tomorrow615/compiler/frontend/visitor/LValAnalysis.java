package io.github.tomorrow615.compiler.frontend.visitor;

import io.github.tomorrow615.compiler.frontend.symbol.Symbol;
import io.github.tomorrow615.compiler.frontend.symbol.SymbolType;

public record LValAnalysis(Symbol symbol, SymbolType resultingType) {
}