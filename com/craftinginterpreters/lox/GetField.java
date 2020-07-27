package com.craftinginterpreters.lox;

import java.util.List;

//This class is helper for getter fields in class, it is almost like LoxFunction

class GetField implements LoxCallable{

    final Token name;
    final List<Stmt> body;
    private Environment closure;
    final boolean staticField;

    GetField(Environment closure,Token name,List<Stmt> body,boolean staticField){
        this.closure = closure;
        this.name = name;
        this.body = body;
        this.staticField = staticField;
    }

    @Override
    public Object call(Interpreter interpreter,List<Object> arguments){
        Environment environment = new Environment(closure);

        try{
            interpreter.executeBlock(body,environment);
        }catch (Return returnValue){
            return returnValue.value;
        }

        return null;
    }

    @Override
    public int arity(){
        return 0;
    }

    GetField bind(LoxInstance instance){
        Environment environment = new Environment(closure);
        environment.define("this",instance);
        return new GetField(environment,name,body,false);
    }

    GetField bindStatic(Environment staticEnvironment){
        return new GetField(staticEnvironment,name,body,true);
    }

    public boolean isStatic(){
        return staticField;
    }
}