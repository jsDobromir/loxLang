package com.craftinginterpreters.lox;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import static com.craftinginterpreters.lox.TokenType.*;

public class Parser {

    private static class ParseError extends RuntimeException{}

    private final List<Token> tokens;
    private int current = 0;
    private boolean whileOpened = false;

    Parser(List<Token> tokens){
        this.tokens = tokens;
    }

    private Stmt declaration(){
        try {
            if(match(CLASS)){
                return classDeclaration();
            }
            if(match(FUN)){
                return function("function",false,null);
            }
            if (match(VAR)){
                return varDecl();
            }
            return statement();
        }catch (ParseError error){
            synchronize();
            return null;
        }
    }

    private Stmt classDeclaration(){
        Token name = consume(IDENTIFIER,"Expect class name.");
        Expr.Variable superclass = null;
        if(match(LESS)){
            consume(IDENTIFIER,"Expect superclass name.");
            superclass = new Expr.Variable(previous());
        }
        consume(LEFT_BRACE,"Expect '{' before class body.");

        List<Stmt> methods = new ArrayList<>();
        while(!check(RIGHT_BRACE) && !isAtEnd()){
            methods.add(classFields());
        }

        consume(RIGHT_BRACE,"Expect '}' after class body.");
        return new Stmt.Class(name,superclass,methods);
    }

    private Stmt classFields(){
        boolean staticMethod = false;
        if(check(STATIC)){
            staticMethod = true;
            advance();
        }

        if(staticMethod){
            if(check(VAR)){
                //static variable declaration
                advance();
                return staticVarDecl();
            }
            else if(check(IDENTIFIER)){
                Token name = advance();
                if(check(LEFT_BRACE)){
                    advance();
                    List<Stmt> body = block();
                    return new Stmt.GetField(name,body,true);
                }
                else{
                    return function("method",true,name);
                }
            }else{
                //Parsing error
                throw error(peek(),"Bad syntax,check again.");
            }
        }
        else{
            //Its not a static field
            Token name = consume(IDENTIFIER,"Expect method name.");
            if(check(LEFT_BRACE)){
                advance();
                List<Stmt> body = block();
                return new Stmt.GetField(name,body,false);
            }else{
                return function("method",false,name);
            }
        }
    }


    private Stmt.Function function(String kind,boolean staticMethod,Token name){
        if(name==null){
            name = consume(IDENTIFIER,"Expect " + kind + " name.");
        }

        consume(LEFT_PAREN,"Expect '(' after " + kind + " name.");
        List<Token> parameters = new ArrayList<>();
        if(!check(RIGHT_PAREN)){
            do{
                if(parameters.size() >=255){
                    error(peek(),"Cannot have more than 255 parameters.");
                }

                parameters.add(consume(IDENTIFIER,"Expect parameter name"));
            }while(match(COMMA));
        }
        consume(RIGHT_PAREN,"Expect ')' after parameters.");

        consume(LEFT_BRACE,"Expect '{' before " + kind + " body.");
        List<Stmt> body = block();
        return new Stmt.Function(name,parameters,body,staticMethod);
    }

    //This method is to make static variable,
    //it is needed to differiantate by ordinary variable in the resolver
    //the code is same live normal varDecl
    private Stmt staticVarDecl(){
        Token name = consume(IDENTIFIER,"Expect variable name.");

        Expr initializer = null;
        if(match(EQUAL)){
            //Should be StmtExpression here to support ternary statement - ?:
            initializer = expression();
        }
        consume(SEMICOLON,"Expect ';' after variable declaration.");
        return new Stmt.StaticVar(name,initializer);
    }

    private Stmt varDecl(){
        Token name = consume(IDENTIFIER,"Expect variable name.");

        Expr initializer = null;
        if(match(EQUAL)){
            //Should be StmtExpression here to support ternary statement - ?:
            initializer = expression();
        }
        consume(SEMICOLON,"Expect ';' after variable declaration.");
        return new Stmt.Var(name,initializer);
    }

    private Stmt statement(){
        if(match(FOR)){
            return forStatement();
        }
        if(match(IF)){
            return ifStatement();
        }
        if(match(WHILE)){
            return whileStatement();
        }
        if(match(PRINT)){
            return printStatement();
        }

        if(match(RETURN)){
            return returnStatement();
        }

        if(match(LEFT_BRACE)){
            return new Stmt.Block(block());
        }

        if(match(BREAK)){
//            if(!whileOpened){
//                throw error(peek(),"Break used at wrong place");
//            }
            consume(SEMICOLON,"Missing semicolon after break");
            return new Stmt.Break();
        }

        return expressionStatement();
    }

    private Stmt forStatement(){
        consume(LEFT_PAREN,"Expect '(' after 'for'");

        Stmt initializer;
        if(match(SEMICOLON)){
            initializer = null;
        }
        else if(match(VAR)){
            initializer = varDecl();
        }else{
            initializer = expressionStatement();
        }

        Expr condition = null;
        if(!check(SEMICOLON)){
            condition = expression();
        }
        consume(SEMICOLON,"Expect ';' after loop condition.");

        Expr increment = null;
        if(!check(RIGHT_PAREN)){
            increment = expression();
        }
        consume(RIGHT_PAREN,"Expect ')' after for clauses.");

        Stmt body = statement();

        if(increment!=null){
            body = new Stmt.Block(Arrays.asList(body,new Stmt.Expression(increment)));
        }

        if(condition==null)condition = new Expr.Literal(true,TokenType.TRUE);
        body = new Stmt.While(condition,body);

        if(initializer!=null){
            body = new Stmt.Block(Arrays.asList(initializer,body));
        }

        return body;
    }

    private Stmt ifStatement(){
        consume(LEFT_PAREN,"Expect '(' after 'if'");
        Expr condition = expression();
        consume(RIGHT_PAREN,"Expect ')' after if condition");

        Stmt thenBranch = statement();
        Stmt elseBranch = null;
        if(match(ELSE)){
            elseBranch = statement();
        }
        return new Stmt.If(condition,thenBranch,elseBranch);
    }

    private Stmt whileStatement(){
        consume(LEFT_PAREN,"Expect '(' after 'while'");
        Expr condition = expression();
        consume(RIGHT_PAREN,"Expect ')' after 'while'");
        whileOpened = true;

        Stmt innserStmt = statement();

        whileOpened = false;
        return new Stmt.While(condition,innserStmt);
    }

    private Stmt printStatement(){
        Expr value = expression();
        consume(SEMICOLON,"Expct ';' after value.");
        return new Stmt.Print(value);
    }

    private Stmt returnStatement(){
        Token keyword = previous();
        Expr value = null;
        if(!check(SEMICOLON)){
            value = expression();
        }

        consume(SEMICOLON,"Expect ';' after return value.");
        return new Stmt.Return(keyword,value);
    }


    private Stmt expressionStatement(){
        Stmt expr = ternaryStatement();
        consume(SEMICOLON, "Expect ';' after expressions.");
        return expr;
    }

    private Stmt ternaryStatement(){
        Expr expr = expression();
        if(match(QUESTION)){
            Stmt first = ternaryStatement();
            consume(DOUBLECOLON,"Badly formed ternary expression");
            Expr right = expression();
            Stmt rightStmt = new Stmt.Expression(right);
            return new Stmt.If(expr,first,rightStmt);
        }
        else{
            return new Stmt.Expression(expr);
        }
    }

    private Stmt funcParamsExpr(){
        if(match(FUN)){
            consume(LEFT_PAREN,"Expect '(' after fun declaration.");
            List<Token> parameters = new ArrayList<>();
            if(!check(RIGHT_PAREN)){
                do{
                    if(parameters.size() >=255){
                        error(peek(),"Cannot have more than 255 parameters.");
                    }

                    parameters.add(consume(IDENTIFIER,"Expect parameter name"));
                }while(match(COMMA));
            }
            consume(RIGHT_PAREN,"Expect ')' after parameters.");

            consume(LEFT_BRACE,"Expect '{' before body.");
            List<Stmt> body = block();
            return new Stmt.Function(new Token(ANONYMOUS,"Anonymous",null,1),parameters,body,false);
        }
        else {
            return new Stmt.Expression(expression());
        }
    }

    private Expr expression(){
        return assignment();
    }


    private Expr assignment(){
        Expr expr = logicalOr();

        if(match(EQUAL)){
            Token equals = previous();
            Expr value = assignment();

            if(expr instanceof Expr.Variable){
                Token name = ((Expr.Variable)expr).name;
                return new Expr.Assign(name,value);
            }
            else if(expr instanceof Expr.Get){
                Expr.Get get = (Expr.Get)expr;
                return new Expr.Set(get.object,get.name,value);
            }

            error(equals,"Invalid assignment target.");
        }

        return expr;
    }

//    private Expr ternary(){
//        Expr expr= logicalOr();
//
//
//        if(match(QUESTION)){
//            Stmt first = statement();
//            consume(DOUBLECOLON,"Badly formed ternary expression");
//            Stmt right = statement();
//
//            expr = new Expr.Binary(expr,operator, new Expr.Binary(first,new Token(TokenType.DOUBLECOLON,":",null,1),right) );
//        }
//
//        return expr;
//    }

    private Expr logicalOr(){
        Expr expr = logicalAnd();
        while (match(OR)){
            Token operator = previous();
            Expr right = logicalAnd();
            expr = new Expr.Logical(expr,operator,right);
        }

        return expr;
    }

    private Expr logicalAnd(){
        Expr expr = equality();

        while (match(AND)){
            Token operator = previous();
            Expr right = equality();
            expr = new Expr.Logical(expr,operator,right);
        }

        return expr;
    }

    private Expr equality(){
        Expr expr = comparision();

        while(match(BANG_EQUAL,EQUAL_EQUAL)){
            Token operator = previous();
            Expr right = comparision();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr comparision(){
        Expr expr = addition();

        while(match(GREATER,GREATER_EQUAL,LESS,LESS_EQUAL)){
            Token operator = previous();
            Expr right = addition();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr addition(){
        Expr expr = multiplication();

        while(match(MINUS,PLUS)){
            Token operator = previous();
            Expr right = multiplication();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr multiplication(){
        Expr expr = unary();

        while(match(SLASH,STAR)){
            Token operator = previous();
            Expr right = unary();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr unary(){
        if(match(BANG,MINUS)){
            Token operator = previous();
            Expr right = unary();
            return new Expr.Unary(operator, right);
        }

        return call();
    }

    private Expr call(){
        Expr expr = primary();

        while(true){
            if(match(LEFT_PAREN)){
                expr = finishCall(expr);
            }
            else if(match(DOT)){
                Token name = consume(IDENTIFIER,"Expect property name after '.'.");
                expr = new Expr.Get(expr,name);
            }
            else{
                break;
            }
        }

        return expr;
    }

    private Expr finishCall(Expr callee){
        List<Stmt> arguments = new ArrayList<>();
        if(!check(RIGHT_PAREN)){
            do{
                if(arguments.size()>=255){
                    error(peek(),"Cannot have more than 255 arguments.");
                }

                arguments.add(funcParamsExpr());
            }while(match(COMMA));
        }

        Token paren = consume(RIGHT_PAREN,"Expect ')' after arguments.");

        return new Expr.Call(callee,paren,arguments);
    }

    private Expr primary(){
        if(match(FALSE)) return new Expr.Literal(false,TokenType.FALSE);
        if(match(TRUE)) return new Expr.Literal(true,TokenType.TRUE);
        if(match(NIL))  return new Expr.Literal(null,TokenType.NIL);

        if(match(NUMBER, STRING)){
            return new Expr.Literal(previous().literal,previous().type);
        }
        if(match(LEFT_PAREN)){
            Expr expr = expression();
            consume(RIGHT_PAREN,"Expect ')' after expression.");
            return new Expr.Grouping(expr);
        }
        if(match(SUPER)){
            Token keyword = previous();
            consume(DOT,"Expect '.' after 'super'.");
            Token method = consume(IDENTIFIER,"Expect superclass method name.");
            return new Expr.Super(keyword,method);
        }
        if(match(THIS)){
            return new Expr.This(previous());
        }

        if(match(IDENTIFIER)){
            //System.out.println("Identifier : " + previous().lexeme);
            return new Expr.Variable(previous());
        }
        throw error(peek(),"Expect expression.");
    }

    private List<Stmt> block() {
        List<Stmt> statements = new ArrayList<>();

        while (!check(RIGHT_BRACE) && !isAtEnd()){
            statements.add(declaration());
        }

        consume(RIGHT_BRACE,"Expect '}' after block.");
        return statements;
    }

    private boolean match(TokenType... types){
        for(TokenType type : types){
            if(check(type)){
                advance();
                return true;
            }
        }

        return false;
    }

    private Token consume(TokenType type,String message){
        if(check(type))return advance();

        throw error(peek(),message);
    }

    private void synchronize(){
        advance();

        while (!isAtEnd()){
            if(previous().type==SEMICOLON) return;

            switch (peek().type){
                case CLASS:
                case FUN:
                case VAR:
                case FOR:
                case IF:
                case WHILE:
                case PRINT:
                case RETURN:
                    return;
            }
            advance();
        }
    }

    private boolean check(TokenType type){
        if(isAtEnd()) return false;
        return peek().type==type;
    }

    private Token advance(){
        if(!isAtEnd()) current++;
        return previous();
    }

    private boolean isAtEnd(){
        return peek().type == EOF;
    }

    private Token peek(){
        return tokens.get(current);
    }

    private Token previous(){
        return tokens.get(current-1);
    }

    private ParseError error(Token token,String message){
        Lox.error(token,message);
        return new ParseError();
    }


    public List<Stmt> parse(){
        List<Stmt> statements = new ArrayList<>();
        while (!isAtEnd()){
            statements.add(declaration());
        }

        return statements;
    }

    //public static void main(String[] args){
//        Token first = new Token(LEFT_PAREN,null,"(",1);
//        Token second = new Token(NUMBER,null,"1",1);
//        Token third = new Token(PLUS,"+",null,1);
//        Token fourth = new Token(NUMBER,null,"5",1);
//        //Token fifth = new Token(RIGHT_PAREN,null,")",1);
//        Token eof = new Token(EOF,null,"EOF",1);
//        List<Token> list = Arrays.asList(first,second,third,fourth,eof);
//
//        Parser parser = new Parser(list);
//        Expr localExpr = parser.expression();
//        PrintTree printTree = new PrintTree();
//
//        System.out.println(printTree.print(localExpr));

    //}
}