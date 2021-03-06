/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.util.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: anna
 */
public class PsiMethodReferenceUtil {
  public static final Logger LOG = Logger.getInstance("#" + PsiMethodReferenceUtil.class.getName());

  public static boolean isSecondSearchPossible(PsiType[] parameterTypes,
                                               QualifierResolveResult qualifierResolveResult,
                                               PsiMethodReferenceExpression methodRef) {
    if (parameterTypes.length > 0 &&
        !(parameterTypes[0] instanceof PsiPrimitiveType) &&
        !methodRef.isConstructor() &&
        isStaticallyReferenced(methodRef) &&
        isReceiverType(parameterTypes[0], qualifierResolveResult.getContainingClass(), qualifierResolveResult.getSubstitutor())) {
      return true;
    }
    return false;
  }

  public static boolean isResolvedBySecondSearch(@NotNull PsiMethodReferenceExpression methodRef,
                                                 @Nullable MethodSignature signature,
                                                 boolean varArgs,
                                                 boolean isStatic,
                                                 int parametersCount) {
    if (signature == null) {
      return false;
    }
    final QualifierResolveResult qualifierResolveResult = getQualifierResolveResult(methodRef);
    final PsiType[] functionalMethodParameterTypes = signature.getParameterTypes();
    return (parametersCount + 1 == functionalMethodParameterTypes.length && !varArgs ||
            varArgs && functionalMethodParameterTypes.length > 0 && !isStatic) &&
           isSecondSearchPossible(functionalMethodParameterTypes, qualifierResolveResult, methodRef);
  }

  public static boolean isCorrectAssignment(PsiType[] parameterTypes,
                                            PsiType[] argTypes,
                                            boolean varargs,
                                            int offset) {
    final int min = Math.min(parameterTypes.length, argTypes.length - offset);
    for (int i = 0; i < min; i++) {
      final PsiType argType = argTypes[i + offset];
      PsiType parameterType = parameterTypes[i];
      parameterType = GenericsUtil.getVariableTypeByExpressionType(parameterType, true);
      if (varargs && i == parameterTypes.length - 1) {
        if (!TypeConversionUtil.isAssignable(parameterType, argType) &&
            !TypeConversionUtil.isAssignable(((PsiArrayType)parameterType).getComponentType(), argType)) {
          return false;
        }
      }
      else if (!TypeConversionUtil.isAssignable(parameterType, argType)) {
        return false;
      }
    }
    return !varargs || parameterTypes.length - 1 <= argTypes.length - offset;
  }

  @Nullable
  public static PsiType getQualifierType(PsiMethodReferenceExpression expression) {
    PsiType qualifierType = null;
    final PsiTypeElement typeElement = expression.getQualifierType();
    if (typeElement != null) {
      qualifierType = typeElement.getType();
    } else {
      final PsiElement qualifier = expression.getQualifier();
      if (qualifier instanceof PsiExpression) {
        qualifierType = ((PsiExpression)qualifier).getType();
      }
    }
    if (qualifierType == null) {
      final QualifierResolveResult qualifierResolveResult = getQualifierResolveResult(expression);
      final PsiClass containingClass = qualifierResolveResult.getContainingClass();
      if (containingClass == null) {
        return null;
      }
      qualifierType = JavaPsiFacade.getElementFactory(expression.getProject()).createType(containingClass);
    }
    return qualifierType;
  }

  public static class QualifierResolveResult {
    private final PsiClass myContainingClass;
    private final PsiSubstitutor mySubstitutor;
    private final boolean myReferenceTypeQualified;

    public QualifierResolveResult(PsiClass containingClass, PsiSubstitutor substitutor, boolean referenceTypeQualified) {
      myContainingClass = containingClass;
      mySubstitutor = substitutor;
      myReferenceTypeQualified = referenceTypeQualified;
    }

    @Nullable
    public PsiClass getContainingClass() {
      return myContainingClass;
    }

    public PsiSubstitutor getSubstitutor() {
      return mySubstitutor;
    }

    public boolean isReferenceTypeQualified() {
      return myReferenceTypeQualified;
    }
  }

  public static boolean isValidQualifier(PsiMethodReferenceExpression expression) {
    final PsiElement referenceNameElement = expression.getReferenceNameElement();
    if (referenceNameElement instanceof PsiKeyword) {
      final PsiElement qualifier = expression.getQualifier();
      if (qualifier instanceof PsiTypeElement) {
        return true;
      }
      if (qualifier instanceof PsiReferenceExpression && ((PsiReferenceExpression)qualifier).resolve() instanceof PsiClass) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  public static QualifierResolveResult getQualifierResolveResult(@NotNull PsiMethodReferenceExpression methodReferenceExpression) {
    PsiClass containingClass = null;
    PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
    final PsiExpression expression = methodReferenceExpression.getQualifierExpression();
    if (expression != null) {
      final PsiType expressionType = replaceArrayType(expression.getType(), expression);
      PsiClassType.ClassResolveResult result = PsiUtil.resolveGenericsClassInType(expressionType);
      containingClass = result.getElement();
      if (containingClass != null) {
        substitutor = result.getSubstitutor();
      }
      if (containingClass == null && expression instanceof PsiReferenceExpression) {
        final JavaResolveResult resolveResult = ((PsiReferenceExpression)expression).advancedResolve(false);
        final PsiElement resolve = resolveResult.getElement();
        if (resolve instanceof PsiClass) {
          containingClass = (PsiClass)resolve;
          substitutor = resolveResult.getSubstitutor();
          return new QualifierResolveResult(containingClass, substitutor, true);
        }
      }
    }
    else {
      final PsiTypeElement typeElement = methodReferenceExpression.getQualifierType();
      if (typeElement != null) {
        PsiType type = replaceArrayType(typeElement.getType(), typeElement);
        PsiClassType.ClassResolveResult result = PsiUtil.resolveGenericsClassInType(type);
        containingClass = result.getElement();
        if (containingClass != null) {
          return new QualifierResolveResult(containingClass, result.getSubstitutor(), true);
        }
      }
    }
    return new QualifierResolveResult(containingClass, substitutor, false);
  }
  
  public static boolean isStaticallyReferenced(@NotNull PsiMethodReferenceExpression methodReferenceExpression) {
    final PsiExpression qualifierExpression = methodReferenceExpression.getQualifierExpression();
    if (qualifierExpression != null) {
      return qualifierExpression instanceof PsiReferenceExpression &&
             ((PsiReferenceExpression)qualifierExpression).resolve() instanceof PsiClass;
    }
    return true;
  }

  //if P1, ..., Pn is not empty and P1 is a subtype of ReferenceType, then the method reference expression is treated as 
  // if it were a method invocation expression with argument expressions of types P2, ...,Pn.
  public static boolean isReceiverType(@Nullable PsiType receiverType, PsiClass containingClass, PsiSubstitutor psiSubstitutor) {
    if (receiverType == null) {
      return false;
    }
    return TypeConversionUtil.isAssignable(JavaPsiFacade.getElementFactory(containingClass.getProject()).createType(containingClass, psiSubstitutor), 
                                           replaceArrayType(receiverType, containingClass));
  }

  public static PsiType getFirstParameterType(PsiType functionalInterfaceType, PsiElement context) {
    final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(functionalInterfaceType);
    final MethodSignature function = LambdaUtil.getFunction(resolveResult.getElement());
    if (function != null) {
      final int interfaceMethodParamsLength = function.getParameterTypes().length;
      if (interfaceMethodParamsLength > 0) {
        PsiType type = resolveResult.getSubstitutor().substitute(function.getParameterTypes()[0]);
        return type != null ? PsiUtil.captureToplevelWildcards(type, context) : null;
      }
    }
    return null;
  }

  private static PsiType replaceArrayType(PsiType type, @NotNull PsiElement context) {
    if (type instanceof PsiArrayType) {
      type = JavaPsiFacade.getElementFactory(context.getProject())
        .getArrayClassType(((PsiArrayType)type).getComponentType(), PsiUtil.getLanguageLevel(context));
    }
    return type;
  }

  public static String checkMethodReferenceContext(PsiMethodReferenceExpression methodRef) {
    final PsiElement resolve = methodRef.resolve();

    if (resolve == null) return null;
    return checkMethodReferenceContext(methodRef, resolve, methodRef.getFunctionalInterfaceType());
  }

  public static String checkMethodReferenceContext(PsiMethodReferenceExpression methodRef,
                                                   PsiElement resolve,
                                                   PsiType functionalInterfaceType) {
    final PsiClass containingClass = resolve instanceof PsiMethod ? ((PsiMethod)resolve).getContainingClass() : (PsiClass)resolve;
    final boolean isStaticSelector = isStaticallyReferenced(methodRef);
    final PsiElement qualifier = methodRef.getQualifier();

    boolean isMethodStatic = false;
    boolean receiverReferenced = false;
    boolean isConstructor = true;

    if (resolve instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)resolve;

      isMethodStatic = method.hasModifierProperty(PsiModifier.STATIC);
      isConstructor = method.isConstructor();

      final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(functionalInterfaceType);
      final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(resolveResult);
      receiverReferenced = isResolvedBySecondSearch(methodRef,
                                                    interfaceMethod != null ? interfaceMethod.getSignature(LambdaUtil.getSubstitutor(interfaceMethod, resolveResult)) : null, 
                                                    method.isVarArgs(), 
                                                    isMethodStatic,
                                                    method.getParameterList().getParametersCount());

      if (method.hasModifierProperty(PsiModifier.ABSTRACT) && qualifier instanceof PsiSuperExpression) {
        return "Abstract method '" + method.getName() + "' cannot be accessed directly";
      }
    }

    if (!receiverReferenced && isStaticSelector && !isMethodStatic && !isConstructor) {
      return "Non-static method cannot be referenced from a static context";
    }

    if (!receiverReferenced && !isStaticSelector && isMethodStatic) {
      return "Static method referenced through non-static qualifier";
    }

    if (receiverReferenced && isStaticSelector && isMethodStatic && !isConstructor) {
      return "Static method referenced through receiver";
    }

    if (isMethodStatic && isStaticSelector && qualifier instanceof PsiTypeElement) {
      final PsiJavaCodeReferenceElement referenceElement = PsiTreeUtil.getChildOfType(qualifier, PsiJavaCodeReferenceElement.class);
      if (referenceElement != null) {
        final PsiReferenceParameterList parameterList = referenceElement.getParameterList();
        if (parameterList != null && parameterList.getTypeArguments().length > 0) {
          return "Parameterized qualifier on static method reference";
        }
      }
    }

    if (isConstructor) {
      if (containingClass != null && PsiUtil.isInnerClass(containingClass) && containingClass.isPhysical()) {
        PsiClass outerClass = containingClass.getContainingClass();
        if (outerClass != null && !InheritanceUtil.hasEnclosingInstanceInScope(outerClass, methodRef, true, false)) {
           return "An enclosing instance of type " + PsiFormatUtil.formatClass(outerClass, PsiFormatUtilBase.SHOW_NAME) + " is not in scope";
        }
      }
    }
    return null;
  }

  public static String checkTypeArguments(PsiTypeElement qualifier, PsiType psiType) {
    if (psiType instanceof PsiClassType) {
      final PsiJavaCodeReferenceElement referenceElement = qualifier.getInnermostComponentReferenceElement();
      if (referenceElement != null) {
        PsiType[] typeParameters = referenceElement.getTypeParameters();
        for (PsiType typeParameter : typeParameters) {
          if (typeParameter instanceof PsiWildcardType) {
            return "Unexpected wildcard";
          }
        }
      }
    }
    return null;
  }

  public static String checkReturnType(PsiMethodReferenceExpression expression, JavaResolveResult result, PsiType functionalInterfaceType) {
    final PsiElement resolve = result.getElement();
    if (resolve instanceof PsiMethod) {
      final PsiClass containingClass = ((PsiMethod)resolve).getContainingClass();
      LOG.assertTrue(containingClass != null);
      PsiSubstitutor subst = result.getSubstitutor();
      PsiClass qContainingClass = getQualifierResolveResult(expression).getContainingClass();
      if (qContainingClass != null && isReceiverType(getFirstParameterType(functionalInterfaceType, expression), qContainingClass,  subst)) {
        subst = TypeConversionUtil.getClassSubstitutor(containingClass, qContainingClass, subst);
        LOG.assertTrue(subst != null);
      }


      final PsiType interfaceReturnType = LambdaUtil.getFunctionalInterfaceReturnType(functionalInterfaceType);

      PsiType returnType = PsiTypesUtil.patchMethodGetClassReturnType(expression, expression,
                                                                      (PsiMethod)resolve, null,
                                                                      PsiUtil.getLanguageLevel(expression));
      if (returnType == null) {
        returnType = ((PsiMethod)resolve).getReturnType();
      }
      PsiType methodReturnType = subst.substitute(returnType);
      if (interfaceReturnType != null && !PsiType.VOID.equals(interfaceReturnType)) {
        if (methodReturnType == null) {
          methodReturnType = JavaPsiFacade.getElementFactory(expression.getProject()).createType(containingClass, subst);
        }
        if (!TypeConversionUtil.isAssignable(interfaceReturnType, methodReturnType)) {
          return "Bad return type in method reference: cannot convert " + methodReturnType.getCanonicalText() + " to " + interfaceReturnType.getCanonicalText();
        }
      }
    }
    return null;
  }
}
