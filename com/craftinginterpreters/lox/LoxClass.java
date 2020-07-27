package com.craftinginterpreters.lox;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;

class LoxClass implements LoxCallable{
    final String name;
    final LoxClass superclass;
    private final Map<String, LoxFunction> methods;
    private final Map<String, GetField> getFields;
    final Environment staticEnvironment;
    LoxClass(String name,LoxClass superclass,Map<String, LoxFunction> methods,Map<String, GetField> getFields,Environment staticEnvironment){
        this.name = name;
        this.superclass = superclass;
        this.methods = methods;
        this.getFields = getFields;
        this.staticEnvironment = staticEnvironment;
    }

    LoxFunction findMethod(String name){
        if(methods.containsKey(name)){
            return methods.get(name);
        }

        if(superclass!=null){
            return superclass.findMethod(name);
        }

        return null;
    }


    GetField findGetField(String name){
        if(getFields.containsKey(name)){
            return getFields.get(name);
        }

        return null;
    }

//    private void makeMethods(List<Stmt.Function> methods){
//        for(Stmt.Function func : methods){
//            LoxCallable function = new LoxFunction(func,environment);
//            environment.define(func.name.lexeme,function);
//        }
//    }

    @Override
    public String toString(){
        return name;
    }

    @Override
    public Object call(Interpreter interpreter,List<Object> arguments){
        LoxInstance instance = new LoxInstance(this);
        LoxFunction initializer = findMethod("init");
        if(initializer!=null){
            initializer.bind(instance).call(interpreter,arguments);
        }
        return instance;
    }

    @Override
    public int arity(){
        LoxFunction initializer = findMethod("init");
        if (initializer == null) return 0;
        return initializer.arity();
    }
}