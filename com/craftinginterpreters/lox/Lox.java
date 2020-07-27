package com.craftinginterpreters.lox;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.io.File;

public class Lox {

    static boolean hadError = false;
    static boolean hadRuntimeError = false;

    private static final Interpreter interpreter = new Interpreter();

    public static void main(String[] args) throws IOException {
        if(args.length>1){
            System.out.println("Usage: jlox [script]");
            System.exit(64);
        }else if(args.length==1){
            String userDirectory = new File("").getAbsolutePath() + "/com/craftinginterpreters/lox/" + args[0];
            runFile(userDirectory);
        }else{
            runPrompt();
        }
    }

    //runfile method,if given file from command line will run it
    private static void runFile(String path) throws IOException{
        byte[] bytes = Files.readAllBytes(Paths.get(path));
        run(new String(bytes, Charset.defaultCharset()));
        if(hadError){
            System.exit(65);
        }
        if(hadRuntimeError) System.exit(70);
    }

    private static void runPrompt() throws IOException{
        InputStreamReader input = new InputStreamReader(System.in);
        BufferedReader reader = new BufferedReader(input);

        for(;;){
            System.out.println("> ");
            String line = reader.readLine();
            run(line);
            if(line==null)break;
            hadError = false;
        }
    }

    private static void run(String source){
        Scanner scanner = new Scanner(source);
        List<Token> tokens = scanner.scanTokens();

        Parser parser = new Parser(tokens);
        Resolver resolver = new Resolver(interpreter);
        List<Stmt> statements= parser.parse();


        if(hadError)return;

        resolver.resolve(statements);

        interpreter.interpret(statements);

    }

    static void error(Token token,String message){
        if(token.type==TokenType.EOF){
            report(token.line," at end",message);
        }else{
            report(token.line," at '" + token.lexeme +"'",message);
        }
    }

    static void runtimeError(RuntimeError error){
        System.err.println(error.getMessage() + "\n[line " + error.token.line + "]");
        hadRuntimeError = true;
    }

    static void report(int line,String where,String message){
        System.err.println("[line " + line + "] Error at " + where + ": " + message);
        hadError = true;
    }
}