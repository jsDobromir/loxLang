package com.craftinginterpreters.lox;



enum TokenType {
    //Single character tokens
    LEFT_PAREN, RIGHT_PAREN, LEFT_BRACE, RIGHT_BRACE,
    COMMA, DOT, MINUS, PLUS, SEMICOLON, SLASH, STAR,

    //One or two characters tokens
    BANG, BANG_EQUAL,
    EQUAL, EQUAL_EQUAL,
    GREATER, GREATER_EQUAL,
    LESS, LESS_EQUAL,QUESTION,DOUBLECOLON,
    FIRST,SECOND,
    //Literals
    IDENTIFIER, STRING, NUMBER,

    //Keywords
    AND, CLASS, ELSE, FALSE, FUN, FOR, IF, NIL, OR,
    PRINT , RETURN, SUPER, THIS, TRUE, VAR, WHILE,BREAK,ANONYMOUS,STATIC,

    //First,Second

    EOF
}