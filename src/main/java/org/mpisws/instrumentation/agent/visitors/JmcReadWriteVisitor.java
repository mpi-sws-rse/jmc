package org.mpisws.instrumentation.agent.visitors;

import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.field.FieldList;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.pool.TypePool;

public class JmcReadWriteVisitor implements AsmVisitorWrapper {
    @Override
    public int mergeWriter(int i) {
        return 0;
    }

    @Override
    public int mergeReader(int i) {
        return 0;
    }

    @Override
    public ClassVisitor wrap(
            TypeDescription typeDescription,
            ClassVisitor classVisitor,
            Implementation.Context context,
            TypePool typePool,
            FieldList<FieldDescription.InDefinedShape> fieldList,
            MethodList<?> methodList,
            int i,
            int i1) {
        return null;
    }
}
