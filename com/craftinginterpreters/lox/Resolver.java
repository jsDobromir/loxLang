package com.craftinginterpreters.lox;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.Objects;

class Resolver implements Expr.Visitor<Void>, Stmt.Visitor<Void>{
    private final Interpreter intepreter;
    private final Stack<Map<String,Boolean>> scopes = new Stack<>();
    //for static fields in class
    private final Stack<Map<String,Boolean>> staticClassScope = new Stack<>();

    private FunctionType currentFunction = FunctionType.NONE;
    private LoopType currentLoop = LoopType.NONE;
    private getField currentField = getField.NONE;
    private varType currentVar = varType.NONE;
    private enum FunctionType {
        NONE,
        FUNCTION,
        METHOD,
        STATIC_METHOD,
        INITIALIZER
    }

    private enum getField{
        NONE,
        FIELD,
        STATIC_FIELD
    }

    private enum ClassType{
        NONE,
        CLASS,
        SUBCLASS
    }
    //This is needed to recognise static variable scope
    private enum varType{
        NONE,
        STATIC
    }

    private ClassType currentClass = ClassType.NONE;

    private enum LoopType{
        NONE,
        LOOP
    }
    //This class is helper to show unused variables


    Resolver(Interpreter interpreter){
        this.intepreter = interpreter;
    }

    @Override
    public Void visitBlockStmt(Stmt.Block stmt){
        beginScope(null);
        resolve(stmt.statements);
        endScope();
        return null;
    }

    @Override
    public Void visitFunctionStmt(Stmt.Function stmt){
        if(currentField==getField.STATIC_FIELD || currentFunction==FunctionType.STATIC_METHOD){
            Lox.error(stmt.name,"Lox does not permit nestet function in static fields.");
        }
        declare(stmt.name);
        define(stmt.name);

        resolveFunction(stmt,FunctionType.FUNCTION);

        return null;
    }

    @Override
    public Void visitGetField(Stmt.GetField stmt){
        return null;
    }

    public void resolveGetField(Stmt.GetField getField,getField type){
        getField enclosingGet = currentField;
        currentField = type;
        declare(getField.name);
        define(getField.name);
        beginScope(null);
        resolve(getField.body);
        endScope();
        currentField = enclosingGet;
    }
    //resolving function here ,if its static method
    //it will put variables in the static scope of the class
    private void resolveFunction(Stmt.Function function,FunctionType type){
        FunctionType enclosingFunction = currentFunction;
        currentFunction = type;
        if(currentFunction!=FunctionType.FUNCTION) {
            declare(function.name);
            define(function.name);
        }
        beginScope(null);
        for(Token param : function.params){
            declare(param);
            define(param);
        }
        resolve(function.body);
        endScope();
        currentFunction = enclosingFunction;
    }

    @Override
    public Void visitSuperExpr(Expr.Super expr){
        if(currentClass==ClassType.NONE){
            Lox.error(expr.keyword,
                    "Cannot use 'super' outside of a class.");
        }
        else if (currentClass != ClassType.SUBCLASS) {
            Lox.error(expr.keyword,
                    "Cannot use 'super' in a class with no superclass.");
        }
        resolveLocal(expr,expr.keyword);
        return null;
    }

    @Override
    public Void visitThisExpr(Expr.This expr){
        if(currentClass==ClassType.NONE){
            Lox.error(expr.keyword,"Cannot use 'this' outside of a class.");
            return null;
        }
        resolveLocal(expr,expr.keyword);
        return null;
    }

    @Override
    public Void visitVarStmt(Stmt.Var stmt){
        declare(stmt.name);
        if(stmt.initializer != null){
            resolve(stmt.initializer);
        }
        define(stmt.name);
        return null;
    }
    //resolving static variable here
    //will put them on the static scope of the class,
    //not on the regular one
    @Override
    public Void visitStaticVarStmt(Stmt.StaticVar stmt){
        varType enclosingVar = currentVar;
        currentVar = varType.STATIC;
        declare(stmt.name);
        if(stmt.initializer!=null){
            resolve(stmt.initializer);
        }
        define(stmt.name);
        currentVar = enclosingVar;
        return null;
    }
    //long class resolver method
    //checking whether the Stmt is method,getter or static variable initializer
    //and resolving accordingly to this rules
    @Override
    public Void visitClassStmt(Stmt.Class stmt){
        ClassType enclosingClass = currentClass;
        currentClass = ClassType.CLASS;
        declare(stmt.name);
        define(stmt.name);
        if(stmt.superclass != null && stmt.name.lexeme.equals(stmt.superclass.name.lexeme)){
            Lox.error(stmt.superclass.name,"A class cannot interit from itself.");
        }
        if(stmt.superclass != null){
            currentClass = ClassType.SUBCLASS;
            resolve(stmt.superclass);
        }
        if(stmt.superclass != null){
            beginScope("class");
            scopes.peek().put("super",true);
            staticClassScope.peek().put("super",true);
        }
        beginScope("class");
        scopes.peek().put("this",true);
        for(Stmt method : stmt.methods){
            if(method instanceof Stmt.Function){
                Stmt.Function funcObj = (Stmt.Function) method;
                //if its static method
                if(funcObj.staticMethod){
                    FunctionType declaration = FunctionType.STATIC_METHOD;
                    resolveFunction(funcObj,declaration);
                }
                else {
                    FunctionType declaration = FunctionType.METHOD;
                    if (funcObj.name.lexeme.equals("init")) {
                        declaration = FunctionType.INITIALIZER;
                    }
                    resolveFunction(funcObj, declaration);
                }
            }
            else if(method instanceof Stmt.GetField){
                Stmt.GetField fieldObj = (Stmt.GetField) method;
                if(fieldObj.staticField){
                    resolveGetField(fieldObj,getField.STATIC_FIELD);
                }
                else{
                    resolveGetField(fieldObj,getField.FIELD);
                }
            }
            else if(method instanceof Stmt.StaticVar){
                resolve( ((Stmt.StaticVar)method) );
            }
        }

        endScope();
        if(stmt.superclass != null) endScope();
        currentClass = enclosingClass;

        return null;
    }

    void resolve(List<Stmt> statements){
        for(Stmt stmt : statements){
            resolve(stmt);
        }
    }

    private void resolve(Stmt stmt){
        stmt.accept(this);
    }

    private void resolve(Expr expr){
        expr.accept(this);
    }

    //Declare the variable
    private void declare(Token name){
        //if it is static variabe ,we declare it in the static scope of the class
        if(currentVar==varType.STATIC || currentFunction==FunctionType.STATIC_METHOD || currentField==getField.STATIC_FIELD){
            if(staticClassScope.isEmpty()) return;
            Map<String, Boolean> scope = staticClassScope.peek();
            if(scope.containsKey(name.lexeme)){
                Lox.error(name,"Static variable with this name already declared in this scope.");
            }
            scope.put(name.lexeme,false);
        }
        //else go to regular scope
        else {
            if (scopes.isEmpty()) return;

            Map<String, Boolean> scope = scopes.peek();
            if (scope.containsKey(name.lexeme)) {
                Lox.error(name, "Variable with this name already declared in this scope.");
            }
            scope.put(name.lexeme, false);
        }
    }

    //Define it
    private void define(Token name){
        if(currentVar==varType.STATIC || currentFunction==FunctionType.STATIC_METHOD || currentField==getField.STATIC_FIELD){
            if(staticClassScope.isEmpty()) return;
            staticClassScope.peek().put(name.lexeme,true);
        }
        else {
            if (scopes.isEmpty()) return;
            scopes.peek().put(name.lexeme, true);
        }

    }

    private void beginScope(String kind){
        if(kind!=null && kind.equals("class")){
            scopes.push(new HashMap<String,Boolean>());
            staticClassScope.push(new HashMap<String,Boolean>());
            return;
        }
        if(currentFunction==FunctionType.STATIC_METHOD || currentField==getField.STATIC_FIELD){
            staticClassScope.push(new HashMap<String,Boolean>());
            return;
        }
        scopes.push(new HashMap<String,Boolean>());
    }

    private void endScope(){
        if(currentFunction==FunctionType.STATIC_METHOD || currentField==getField.STATIC_FIELD){
            staticClassScope.pop();
            return;
        }
        scopes.pop();
    }


    //Expressions
    public Void visitVariableExpr(Expr.Variable expr){

        if(!scopes.isEmpty() && scopes.peek().get(expr.name.lexeme) == Boolean.FALSE){
            Lox.error(expr.name,"Cannot read local variable in its own initializer");
        }


        resolveLocal(expr,expr.name);
        return null;
    }

    @Override
    public Void visitAssignExpr(Expr.Assign expr){

        resolve(expr.value);
        resolveLocal(expr,expr.name);
        return null;
    }

    @Override
    public Void visitGetExpr(Expr.Get expr){
        resolve(expr.object);
        return null;
    }

    @Override
    public Void visitSetExpr(Expr.Set expr) {
        resolve(expr.value);
        resolve(expr.object);
        return null;
    }


    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt){
        resolve(stmt.expression);
        return null;
    }

    @Override
    public Void visitIfStmt(Stmt.If stmt){
        resolve(stmt.condition);
        resolve(stmt.thenBranch);
        if(stmt.elseBranch !=null) resolve(stmt.elseBranch);
        return null;
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt){
        resolve(stmt.expression);
        return null;
    }

    @Override
    public Void visitReturnStmt(Stmt.Return stmt){
        if(currentFunction==FunctionType.NONE && currentField==getField.NONE){
            Lox.error(stmt.keyword,"Cannot return from top-level code.");
        }

        if(stmt.value != null){
            if(currentFunction==FunctionType.INITIALIZER){
                Lox.error(stmt.keyword,"Cannot return a value from an initializer");
            }
            resolve(stmt.value);
        }
        return null;
    }

    @Override
    public Void visitWhileStmt(Stmt.While stmt){
        LoopType enclosingLoop = currentLoop;
        currentLoop = LoopType.LOOP;
        resolve(stmt.condition);
        resolve(stmt.statement);

        currentLoop=enclosingLoop;
        return null;
    }

    @Override
    public Void visitBinaryExpr(Expr.Binary expr) {
        resolve(expr.left);
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitCallExpr(Expr.Call expr){
        resolve(expr.callee);

        for(Stmt argument : expr.arguments){
            resolve(argument);
        }

        return null;
    }

    @Override
    public Void visitGroupingExpr(Expr.Grouping expr){
        resolve(expr.expression);
        return null;
    }

    @Override
    public Void visitLiteralExpr(Expr.Literal expr) {
        return null;
    }

    @Override
    public Void visitLogicalExpr(Expr.Logical expr) {
        resolve(expr.left);
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitUnaryExpr(Expr.Unary expr) {
        resolve(expr.right);
        return null;
    }

    @Override
    public Void visitBreakStmt(Stmt.Break expr){
        if(currentLoop!=LoopType.LOOP){
            Lox.error(new Token(TokenType.BREAK,"Break",null,0),"Break should be used in loop.");
        }
        return null;
    }

    private void resolveLocal(Expr expr,Token name){
        //This is impornat block of code ,if we are in static method or static getter intializer
        //we are searching the variable in the staticScope
        if(currentFunction==FunctionType.STATIC_METHOD || currentField==getField.STATIC_FIELD){
            for(int i=staticClassScope.size() -1;i>=0;i--){
                if(staticClassScope.get(i).containsKey(name.lexeme)){
                    intepreter.resolve(expr,staticClassScope.size() -1 -i);
                    return;
                }
            }
        }
        //If we are in the regular scope ,we search there for the variable
        else {
            for (int i = scopes.size() - 1; i >= 0; i--) {
                if (scopes.get(i).containsKey(name.lexeme)) {
                    intepreter.resolve(expr, scopes.size() - 1 - i);
                    return;
                }
            }
        }

        //Not found.Assume it is global
    }


}