package com.craftinginterpreters.lox;
import java.util.Objects;
public class Token {
    
    public TokenType type;
    final String lexeme;
    final Object literal;
    final int line;

    Token(TokenType type,String lexeme,Object literal,int line){
        this.type = type;
        this.lexeme = lexeme;
        this.literal = literal;
        this.line = line;
    }

    public String toString(){
        return type + " " + lexeme + " " + literal;
    }

//    @Override
//    public boolean equals(Object o){
//        if(this==o) return true;
//        if(!(o instanceof Token)){
//            return false;
//        }
//        Token otherToken = (Token) o;
//        return Objects.equals(type,otherToken.type) &&
//                Objects.equals(lexeme,otherToken.lexeme) &&
//                Objects.equals(literal,otherToken.literal);
//    }
//
//    @Override
//    public int hashCode(){
//        return Objects.hash(lexeme,literal);
//    }
}