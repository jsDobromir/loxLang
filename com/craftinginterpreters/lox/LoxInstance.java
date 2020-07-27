package com.craftinginterpreters.lox;
import java.util.Map;
import java.util.HashMap;
class LoxInstance {

    private LoxClass klass;
    private final Map<String, Object> fields = new HashMap<>();

    LoxInstance(){

    }

    LoxInstance(LoxClass klass){
        this.klass = klass;
    }

    public String toString(){
        return klass.name + " instance";
    }

    Object get(Token name){
        if(fields.containsKey(name.lexeme)){
            return fields.get(name.lexeme);
        }

        LoxFunction method = klass.findMethod(name.lexeme);
        if(method!=null){
            if(method.isStaticMethod()){
                return method.bindStatic(klass.staticEnvironment);
            }
            return method.bind(this);
        }

        GetField getField = klass.findGetField(name.lexeme);
        if(getField!=null){
            if(getField.isStatic()){
                //System.out.println(klass.staticEnvironment);
                return getField.bindStatic(klass.staticEnvironment);
            }
            return getField.bind(this);
        }
        throw new RuntimeError(name,"Undefined property '" + name.lexeme + "'.");
    }


    void set(Token name,Object value){
        fields.put(name.lexeme,value);
    }

//
//    public Object get(Token name){
//        //System.out.println("methods : " + klass.methods);
//        Object func = klass.environment.get(name);
//        if(!(func instanceof LoxCallable)){
//            throw new RuntimeError(name,"Non-method called.");
//        }
//        LoxCallable locfunc = (LoxCallable) func;
//
//        return locfunc.call(klass.interpreter,null);
//    }

}