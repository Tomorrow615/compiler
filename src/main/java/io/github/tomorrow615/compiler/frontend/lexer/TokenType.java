package io.github.tomorrow615.compiler.frontend.lexer;

public enum TokenType {
    // 标识符与常量 Identifiers and Constants
    IDENFR,      // 标识符
    INTCON,      // 整型常量
    STRCON,      // 字符串常量

    // 关键字 Keywords
    MAINTK,      // main
    CONSTTK,     // const
    INTTK,       // int
    BREAKTK,     // break
    CONTINUETK,  // continue
    IFTK,        // if
    ELSETK,      // else
    FORTK,       // for
    PRINTFTK,    // printf
    RETURNTK,    // return
    VOIDTK,      // void
    STATICTK,    // static

    // 界符 Delimiters
    LPARENT,     // (
    RPARENT,     // )
    LBRACK,      // [
    RBRACK,      // ]
    LBRACE,      // {
    RBRACE,      // }
    COMMA,       // ,
    SEMICN,      // ;

    // 运算符 Operators
    PLUS,        // +
    MINU,        // -
    MULT,        // *
    DIV,         // /
    MOD,         // %
    LSS,         // <
    LEQ,         // <=
    GRE,         // >
    GEQ,         // >=
    EQL,         // ==
    NEQ,         // !=
    ASSIGN,      // =
    AND,         // &&
    OR,          // ||
    NOT,         // !

    // 文件结束符 End Of File
    EOF
}