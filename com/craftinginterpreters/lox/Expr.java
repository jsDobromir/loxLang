package com.craftinginterpreters.lox;

import java.util.List;

abstract class Expr {
   interface Visitor<R> {
     R visitBinaryExpr(Binary expr);
     R visitGroupingExpr(Grouping expr);
     R visitLiteralExpr(Literal expr);
     R visitUnaryExpr(Unary expr);
     R visitVariableExpr(Variable expr);
     R visitAssignExpr(Assign expr);
     R visitLogicalExpr(Logical expr);
     R visitCallExpr(Call expr);
     R visitGetExpr(Get expr);
     R visitSetExpr(Set expr);
     R visitThisExpr(This expr);
     R visitSuperExpr(Super expr);
  }

   static class Binary extends Expr {
     Binary(Expr left, Token operator, Expr right)  {
        this.left = left;
        this.operator = operator;
        this.right = right;
    }

    @Override
    <R> R accept(Visitor<R> visitor) {
      return visitor.visitBinaryExpr(this);
    }

        final Expr left;
        public Token operator;
        final Expr right;
       public void changeTokenType(TokenType type){
           this.operator.type = type;
       }
}

    static class Call extends Expr{
       Call(Expr callee,Token paren,List<Stmt> arguments){
           this.callee = callee;
           this.paren = paren;
           this.arguments = arguments;
       }

       @Override
       <R> R accept(Visitor<R> visitor){
           return visitor.visitCallExpr(this);
       }

       final Expr callee;
       final Token paren;
       final List<Stmt> arguments;
    }

    static class Get extends Expr {
       Get(Expr object,Token name){
           this.object = object;
           this.name = name;
       }

       @Override
       <R> R accept(Visitor<R> visitor){
           return visitor.visitGetExpr(this);
       }

       final Expr object;
       final Token name;
    }

   static class Grouping extends Expr {
     Grouping(Expr expression)  {
        this.expression = expression;
    }

    @Override
    <R> R accept(Visitor<R> visitor) {
      return visitor.visitGroupingExpr(this);
    }

        final Expr expression;
}


   static class Literal extends Expr {
     Literal(Object value,TokenType type)  {
        this.value = value;
        this.type = type;
    }

    @Override
    <R> R accept(Visitor<R> visitor) {
      return visitor.visitLiteralExpr(this);
    }

        final Object value;
        final TokenType type;
}


   static class Unary extends Expr {
     Unary(Token operator, Expr right)  {
        this.operator = operator;
        this.right = right;
    }

    @Override
    <R> R accept(Visitor<R> visitor) {
      return visitor.visitUnaryExpr(this);
    }

        final Token operator;
        final Expr right;
}

    static class Variable extends Expr {
       Variable(Token name){
           this.name = name;
       }

       @Override
        <R> R accept(Visitor<R> visitor) {return visitor.visitVariableExpr(this);}

        final Token name;
    }

    static class Assign extends Expr {
       Assign(Token name,Expr value){
           this.name = name;
           this.value = value;
       }

       @Override
       <R> R accept(Visitor<R> visitor){
           return visitor.visitAssignExpr(this);
       }

       final Token name;
       final Expr value;
    }

    //This is exprimental
    static class Logical extends Expr{
       Logical(Expr left,Token operator,Expr right){
           this.left = left;
           this.operator=operator;
           this.right = right;
       }

       @Override
       <R> R accept(Visitor<R> visitor){
           return visitor.visitLogicalExpr(this);
       }

       final Expr left;
       final Token operator;
       final Expr right;
    }

    static class Set extends Expr {
       Set(Expr object,Token name,Expr value){
           this.object = object;
           this.name = name;
           this.value = value;
       }

       @Override
       <R> R accept(Visitor<R> visitor){
           return visitor.visitSetExpr(this);
       }

       final Expr object;
       final Token name;
       final Expr value;
    }

    static class Super extends Expr {
        Super(Token keyword,Token method){
            this.keyword = keyword;
            this.method = method;
        }

        @Override
        <R> R accept(Visitor<R> visitor){
            return visitor.visitSuperExpr(this);
        }

        final Token keyword;
        final Token method;
    }

    static class This extends Expr {
       This(Token keyword){
           this.keyword = keyword;
       }

       @Override
       <R> R accept(Visitor<R> visitor){
           return visitor.visitThisExpr(this);
       }

       final Token keyword;
    }


   abstract <R> R accept(Visitor<R> visitor);
}