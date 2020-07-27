package com.craftinginterpreters.lox;
import static com.craftinginterpreters.lox.TokenType.*;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

class Interpreter implements Expr.Visitor<Object>,Stmt.Visitor<Void>{

    final Environment globals = new Environment();
    private Environment environment = globals;
    private final Map<Expr,Integer> locals = new HashMap<>();

    private boolean breakIt = false;
    private boolean ifBlock = false;

    public Interpreter(){
        globals.define("clock",new LoxCallable(){
            @Override
            public int arity() {return 0;}

            @Override
            public Object call(Interpreter interpreter,List<Object> arguments){
                return (double)System.currentTimeMillis() / 1000.0;
            }

            @Override
            public String toString() {return "<native fn>";}
        });
    }

    void resolve(Expr expr,int depth){
        locals.put(expr,depth);
    }

    void showResolved(){
        for(Map.Entry<Expr,Integer> entry: locals.entrySet()){
            System.out.println(entry.getKey() + " = " + entry.getValue());
        }
    }

    void interpret(List<Stmt> statements){
        try {
            for(Stmt statement : statements){
                execute(statement);
            }
        }catch (RuntimeError error){
            Lox.runtimeError(error);
        }
    }

    private void execute(Stmt stmt){
        stmt.accept(this);
    }

    private String stringify(Object object){
        if(object==null) return "nil";

        if(object instanceof Double){
            String text = object.toString();
            if(text.endsWith(".0")){
                text = text.substring(0,text.length() - 2);
            }
            return text;
        }

        return object.toString();
    }

    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt){
        Expr expression = (Expr) stmt.expression;
        if(!(expression instanceof Expr.Assign) && !(expression instanceof Expr.Call) &&
            !(expression instanceof Expr.Variable) && !(expression instanceof Expr.Set)){
            //System.out.println(expression);
            Object val = evaluate(stmt.expression);
            if(!ifBlock){
                System.out.println(val);
            }
        }
        else {
            evaluate(stmt.expression);
        }
        return null;
    }

    @Override
    public Void visitPrintStmt(Stmt.Print stmt){
        Object value = evaluate(stmt.expression);
        System.out.println(stringify(value));
        return null;
    }

    @Override
    public Void visitVarStmt(Stmt.Var stmt){
        Object value = null;
        if(stmt.initializer !=null){
            value = evaluate(stmt.initializer);
        }

        environment.define(stmt.name.lexeme,value);
        return null;
    }

    @Override
    public Void visitStaticVarStmt(Stmt.StaticVar stmt){
        Object value = null;
        if(stmt.initializer != null){
            value = evaluate(stmt.initializer);
        }
        environment.define(stmt.name.lexeme,value);
        return null;
    }

    @Override
    public Void visitBlockStmt(Stmt.Block stmt){
        executeBlock(stmt.statements, new Environment(environment));
        return null;
    }

    @Override
    public Void visitIfStmt(Stmt.If stmt){
        Object value = evaluate(stmt.condition);
        if(isTruthy(value)){
            ifBlock = true;
            execute(stmt.thenBranch);
            ifBlock =false;
        }else if(stmt.elseBranch!=null){
            ifBlock = true;
            execute(stmt.elseBranch);
            ifBlock = false;
        }
        return null;
    }

    @Override
    public Void visitWhileStmt(Stmt.While stmt){
        Expr condition = (Expr) stmt.condition;
        Object result = evaluate(condition);
        while (isTruthy(result)){
            execute(stmt.statement);
            if(breakIt){
                breakIt=false;
                break;
            }
            result = evaluate(condition);
        }
        return null;
    }

    @Override
    public Void visitBreakStmt(Stmt.Break stmt){
        breakIt = true;
        return null;
    }

    @Override
    public Void visitClassStmt(Stmt.Class stmt){

        //Superclass first
        Object superclass = null;
        if (stmt.superclass != null) {
            superclass = evaluate(stmt.superclass);
            if (!(superclass instanceof LoxClass)) {
                throw new RuntimeError(stmt.superclass.name,
                        "Superclass must be a class.");
            }
        }

        environment.define(stmt.name.lexeme,null);
        if(stmt.superclass!=null){
            environment = new Environment(environment);
            environment.define("super",superclass);
        }

        Map<String, LoxFunction> methods = new HashMap<>();
        Map<String, GetField> getFields = new HashMap<>();
        Environment staticEnvironment = new Environment(environment);
        for(Stmt field : stmt.methods){
            if(field instanceof Stmt.Function){
                Stmt.Function func = (Stmt.Function) field;
                LoxFunction function = new LoxFunction(func,environment,func.name.lexeme.equals("init"));
                if(func.staticMethod){
                    staticEnvironment.define(func.name.lexeme,function);
                }
                methods.put(func.name.lexeme,function);
            }
            else if(field instanceof Stmt.GetField){
                Stmt.GetField fieldObj = (Stmt.GetField) field;
                GetField fieldInner = new GetField(environment,fieldObj.name,fieldObj.body,fieldObj.staticField);
                if(fieldObj.staticField){
                    staticEnvironment.define(fieldObj.name.lexeme,fieldInner);
                }
                getFields.put(fieldObj.name.lexeme,fieldInner);
            }
            else if(field instanceof Stmt.StaticVar){
                Stmt.StaticVar varObj = (Stmt.StaticVar) field;
                Object value = null;
                if(varObj.initializer !=null){
                    value = evaluate(varObj.initializer);
                }
                staticEnvironment.define(varObj.name.lexeme,value);
            }
        }
        LoxClass klass = new LoxClass(stmt.name.lexeme,(LoxClass)superclass,methods,getFields,staticEnvironment);
        if(superclass!=null){
            environment = environment.enclosing;
        }
        environment.assign(stmt.name,klass);
        return null;
    }

    @Override
    public Void visitFunctionStmt(Stmt.Function stmt){
        LoxFunction function = new LoxFunction(stmt,environment,false);
        environment.define(stmt.name.lexeme,function);
        return null;
    }

    @Override
    public Void visitGetField(Stmt.GetField stmt){

        return null;
    }

    @Override
    public Object visitSuperExpr(Expr.Super expr){
        int distance = locals.get(expr);
        LoxClass superclass = (LoxClass) environment.getAt(distance,"super");

        //"this" is always one level nearer than "supe"'s environment
        LoxInstance object = (LoxInstance) environment.getAt(distance-1,"this");

        LoxFunction method = superclass.findMethod(expr.method.lexeme);
        if(method.isStaticMethod()){
            throw new RuntimeError(expr.method,"Can't call static method this way.");
        }
        if (method == null) {
            throw new RuntimeError(expr.method,
                    "Undefined property '" + expr.method.lexeme + "'.");
        }

        return method.bind(object);
    }


    @Override
    public Void visitReturnStmt(Stmt.Return stmt){
        Object value = null;
        if(stmt.value !=null) value = evaluate(stmt.value);

        throw new Return(value);
    }

    void executeBlock(List<Stmt> statements,Environment environment){
        Environment previous = this.environment;
        try {
            this.environment = environment;
            for(Stmt statement : statements){
                execute(statement);
            }

        }finally {
            this.environment = previous;
        }
    }

    @Override
    public Object visitLiteralExpr(Expr.Literal expr){
        return expr.value;
    }

    @Override
    public Object visitSetExpr(Expr.Set expr){
        Object object = evaluate(expr.object);

        if(!(object instanceof LoxInstance)){
            throw new RuntimeError(expr.name,"Only instances have fields.");
        }

        Object value = evaluate(expr.value);
        ((LoxInstance)object).set(expr.name,value);
        return value;
    }

    @Override
    public Object visitGetExpr(Expr.Get expr){
        Object object = evaluate(expr.object);
        if(object==null){
            throw new RuntimeError(expr.name,"Undefined instance");
        }
        if(object instanceof LoxInstance){
            LoxInstance objInner = (LoxInstance) object;
            Object calledObj = objInner.get(expr.name);
            if(calledObj instanceof GetField){
                return ((GetField)calledObj).call(this,null);
            }
            return calledObj;
        }
        if(object instanceof LoxClass){
            LoxClass loxObj = (LoxClass) object;
            LoxFunction func = (loxObj.findMethod(expr.name.lexeme));
            GetField fieldInner = (loxObj.findGetField(expr.name.lexeme));
            if(func==null && fieldInner==null){
                throw new RuntimeError(expr.name,"Static field does not exists.");
            }
            else{
                if(func==null){
                    if(!fieldInner.isStatic()){
                        throw new RuntimeError(expr.name,"Can only call static method this way.");
                    }
                    return ( fieldInner.bindStatic(loxObj.staticEnvironment)).call(this,null);
                }
                else if(fieldInner==null){
                    if(!func.isStaticMethod()) {
                        throw new RuntimeError(expr.name, "Can only call static method this way.");
                    }
                    return func.bindStatic(loxObj.staticEnvironment);
                }
            }
        }
        throw new RuntimeError(expr.name,"Only instances have properties.");
    }

    @Override
    public Object visitThisExpr(Expr.This expr) {
        return lookupVariable(expr.keyword, expr);
    }

    @Override
    public Object visitGroupingExpr(Expr.Grouping expr){
        return evaluate(expr.expression);
    }

    private Object evaluate(Expr expr){
        return expr.accept(this);
    }

    @Override
    public Object visitUnaryExpr(Expr.Unary expr){
        Object right = evaluate(expr.right);

        switch (expr.operator.type){
            case MINUS:
                return -(double)right;
            case BANG:
                return !isTruthy(right);
        }

        //Unreachable
        return null;
    }

    @Override
    public Object visitLogicalExpr(Expr.Logical expr){
        Object left = evaluate(expr.left);

        if(expr.operator.type == TokenType.OR){
            if(isTruthy(left))  return left;
        }else{
            if(!isTruthy(left)) return left;
        }

        return evaluate(expr.right);
    }
    @Override
    public Object visitCallExpr(Expr.Call expr){
        Object callee = evaluate(expr.callee);
        List<Object> arguments = new ArrayList<>();
        for(Stmt argument : expr.arguments){
            //System.out.println(argument);
            if(argument instanceof Stmt.Function){
                //arguments.add(execute(argument));
                Stmt.Function argFun = (Stmt.Function) argument;
                LoxFunction function = new LoxFunction(argFun,environment,false);
                arguments.add(function);
            }
            else if(argument instanceof Stmt.Expression){
                Stmt.Expression arg =(Stmt.Expression) argument;
                arguments.add(evaluate(arg.expression));
            }
            //arguments.add(evaluate(argument));
        }

        if(!(callee instanceof LoxCallable)){
            throw new RuntimeError(expr.paren,"Can only call functions and classes");
        }

        LoxCallable function = (LoxCallable)callee;
        if(arguments.size() != function.arity()){
            throw new RuntimeError(expr.paren,"Expected " +
                    function.arity() + " arguments bug got" +
                    arguments.size() + ".");
        }
        return function.call(this,arguments);
    }

    private boolean isTruthy(Object object){
        if(object==null) return false;
        if(object instanceof Boolean) return (boolean) object;
        return true;
    }

    @Override
    public Object visitBinaryExpr(Expr.Binary expr){
        Object left = evaluate(expr.left);
        Object right = evaluate(expr.right);

        switch (expr.operator.type){
            case GREATER:
                checkNumberOperands(expr.operator, left, right);
                return checkLiterals(expr.operator,left,right);
            case GREATER_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return checkLiterals(expr.operator,left,right);
            case LESS:
                checkNumberOperands(expr.operator, left, right);
                return checkLiterals(expr.operator,left,right);
            case LESS_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return checkLiterals(expr.operator,left,right);
            case BANG_EQUAL:
                return !isEqual(left,right);
            case EQUAL_EQUAL:
                return isEqual(left,right);
            case MINUS:
                checkNumberOperand(expr.operator,right);
                return (double)left - (double)right;
            case SLASH:
                checkNumberOperands(expr.operator, left, right);
                double val = (double) right;
                if(val!=0){
                    return (double) left / val;
                }
                else{
                    throw new RuntimeError(expr.operator,"Division by 0 is forbidden!!!");
                }
            case STAR:
                checkNumberOperands(expr.operator, left, right);
                return (double)left * (double)right;
            case PLUS:
                if(left instanceof Double && right instanceof Double){
                    return (double)left + (double)right;
                }
                if(left instanceof String && right instanceof String) {
                    return (String) left + (String) right;
                }
                if(left instanceof String && right instanceof Double){
                    String str = (String) left;
                    double dblval = (double) right;
                    str = str.concat(String.valueOf((int)dblval));
                    return str;
                }
                throw new RuntimeError(expr.operator,"Operands must be two numbers or two strings.");
        }

        //Unreachable
        return null;
    }

    @Override
    public Object visitVariableExpr(Expr.Variable expr){
        return lookupVariable(expr.name,expr);
        //return environment.get(expr.name);
    }

    private Object lookupVariable(Token name,Expr expr){
        Integer distance = locals.get(expr);
        if(distance!=null){
            return environment.getAt(distance,name.lexeme);
        }else{
            return globals.get(name);
        }
    }


    @Override
    public Object visitAssignExpr(Expr.Assign expr){
        Object value = evaluate(expr.value);

        Integer distance = locals.get(expr);
        if(distance!=null){
            environment.assignAt(distance,expr.name,value);
        }else{
            globals.assign(expr.name,value);
        }
        return value;
    }

    private boolean isEqual(Object a,Object b){
        if(a==null && b==null) return true;
        if(a==null) return false;

        return a.equals(b);
    }

    private void checkNumberOperand(Token operator,Object operand){
        if(operand instanceof Double) return;
        throw new RuntimeError(operator,"Operand must be a number");
    }

    private void checkNumberOperands(Token operator,Object left,Object right){
        if(left instanceof Double && right instanceof Double) return;
        if(left instanceof String && right instanceof String) return;
        throw new RuntimeError(operator,"Operands must be numbers or strings");
    }

    private boolean checkLiterals(Token operator,Object left,Object right){
        if(left instanceof Double && right instanceof Double){
            switch (operator.type){
                case GREATER:
                    return (double) left > (double) right;
                case GREATER_EQUAL:
                    return (double) left >= (double) right;
                case LESS:
                    return (double) left < (double) right;
                case LESS_EQUAL:
                    return (double) left <= (double) right;
                default:
                    throw new RuntimeError(operator,"Unsupported operator");
            }
        }
        if(left instanceof String && right instanceof String){
            String leftStr = (String) left;
            String rightStr = (String) right;
            int res;
            switch (operator.type){
                case GREATER:
                    res = compareStrings(leftStr,rightStr);
                    if(res==1)return true;
                    else return false;
                case GREATER_EQUAL:
                    res = compareStrings(leftStr,rightStr);
                    if(res==1 || res==0)return true;
                    else return false;
                case LESS:
                    res = compareStrings(leftStr,rightStr);
                    if(res==-1)return true;
                    else return false;
                case LESS_EQUAL:
                    res = compareStrings(leftStr,rightStr);
                    if(res==0 || res==-1)return true;
                    else return false;
            }
        }


        //Should be unreachable
        return false;
    }

    private int compareStrings(String left,String right){
        for(int i=0;i<left.length() && i<right.length();i++){
            if(left.charAt(i) != right.charAt(i)){
                return left.charAt(i) < right.charAt(i) ? -1 : 1;
            }
        }

        return left.length() < right.length() ? -1 : left.length() == right.length() ? 0 : 1;
    }
}