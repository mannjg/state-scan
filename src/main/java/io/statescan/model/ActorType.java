package io.statescan.model;

/**
 * Type of actor that receives method calls within a method body.
 */
public enum ActorType {
    /** Field access: this.field.method() */
    FIELD,

    /** Method parameter: param.method() */
    PARAMETER,

    /** Local variable: localVar.method() */
    LOCAL,

    /** Static class reference: ClassName.staticMethod() */
    STATIC_CLASS,

    /** Newly created object: new Foo().method() */
    NEW_OBJECT
}
