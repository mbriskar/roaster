package org.jboss.forge.parser.java.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;

import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.Modifier.ModifierKeyword;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;
import org.jboss.forge.parser.ParserException;
import org.jboss.forge.parser.java.ReadAnnotation;
import org.jboss.forge.parser.java.ReadAnnotation.Annotation;
import org.jboss.forge.parser.java.ReadImport;
import org.jboss.forge.parser.java.ReadImport.Import;
import org.jboss.forge.parser.java.ReadJavaPackageInfo.JavaPackageInfo;
import org.jboss.forge.parser.java.ReadJavaSource;
import org.jboss.forge.parser.java.ReadMember.Member;
import org.jboss.forge.parser.java.SourceType;
import org.jboss.forge.parser.java.SyntaxError;
import org.jboss.forge.parser.java.Visibility;
import org.jboss.forge.parser.java.ast.AnnotationAccessor;
import org.jboss.forge.parser.java.ast.ModifierAccessor;
import org.jboss.forge.parser.java.ast.TypeDeclarationFinderVisitor;
import org.jboss.forge.parser.java.util.Formatter;
import org.jboss.forge.parser.java.util.Strings;
import org.jboss.forge.parser.java.util.Types;
import org.jboss.forge.parser.spi.JavaParserImpl;
import org.jboss.forge.parser.spi.WildcardImportResolver;

public class JavaPackageInfoImpl implements JavaPackageInfo
{

   public JavaPackageInfoImpl(JavaSource<?> enclosingType, Document document,
            CompilationUnit unit, PackageDeclaration pkg)
   {
      this.enclosingType = enclosingType == null ? this : enclosingType;
      this.document = document;
      this.unit = unit;
      this.pkg = pkg;
   }

   @Override
   public SourceType getSourceType()
   {
      return SourceType.PACKAGEINFO;
   }

   @Override
   public String getName()
   {
      return "package-info";
   }

   private final AnnotationAccessor<JavaPackageInfo, JavaPackageInfo> annotations = new AnnotationAccessor<JavaPackageInfo, JavaPackageInfo>();
   private final ModifierAccessor modifiers = new ModifierAccessor();

   protected final Document document;
   protected final CompilationUnit unit;
   protected final ASTNode pkg;
   protected final JavaSource<?> enclosingType;

   public static ServiceLoader<WildcardImportResolver> loader = ServiceLoader.load(WildcardImportResolver.class);
   private static List<WildcardImportResolver> resolvers;

   @Override
   public JavaSource<?> getEnclosingType()
   {
      return enclosingType;
   }

   /*
    * Annotation modifiers
    */
   @Override
   public Annotation<JavaPackageInfo> addAnnotation()
   {
      return annotations.addAnnotation(this, getPackageDeclaration());
   }

   @Override
   public Annotation<JavaPackageInfo> addAnnotation(final Class<? extends java.lang.annotation.Annotation> clazz)
   {
      return annotations.addAnnotation(this, getPackageDeclaration(), clazz.getName());
   }

   @Override
   public Annotation<JavaPackageInfo> addAnnotation(final String className)
   {
      return annotations.addAnnotation(this, getPackageDeclaration(), className);
   }

   @Override
   public List<Annotation<JavaPackageInfo>> getAnnotations()
   {
      return annotations.getAnnotations(this, getPackageDeclaration());
   }

   @Override
   public boolean hasAnnotation(final Class<? extends java.lang.annotation.Annotation> type)
   {
      return annotations.hasAnnotation(this, getPackageDeclaration(), type.getName());
   }

   @Override
   public boolean hasAnnotation(final String type)
   {
      return annotations.hasAnnotation(this, getPackageDeclaration(), type);
   }

   @Override
   public JavaPackageInfo removeAnnotation(final ReadAnnotation<JavaPackageInfo> annotation)
   {
      return annotations.removeAnnotation(this, getPackageDeclaration(), annotation);
   }

   @Override
   public Annotation<JavaPackageInfo> getAnnotation(final Class<? extends java.lang.annotation.Annotation> type)
   {
      return annotations.getAnnotation(this, getPackageDeclaration(), type);
   }

   @Override
   public Annotation<JavaPackageInfo> getAnnotation(final String type)
   {
      return annotations.getAnnotation(this, getPackageDeclaration(), type);
   }

   /*
    * Import modifiers
    */

   @Override
   public Import addImport(final Class<?> type)
   {
      return addImport(type.getCanonicalName());
   }

   @Override
   public <T extends ReadJavaSource<?>> Import addImport(final T type)
   {
      String qualifiedName = type.getQualifiedName();
      return this.addImport(qualifiedName);
   }

   @Override
   public Import addImport(final ReadImport imprt)
   {
      return addImport(imprt.getQualifiedName()).setStatic(imprt.isStatic());
   }

   @Override
   @SuppressWarnings("unchecked")
   public Import addImport(final String className)
   {
      String strippedClassName = Types.stripGenerics(Types.stripArray(className));
      Import imprt;
      if (Types.isSimpleName(strippedClassName) && !hasImport(strippedClassName))
      {
         throw new IllegalArgumentException("Cannot import class without a package [" + strippedClassName + "]");
      }

      if (!hasImport(strippedClassName) && validImport(strippedClassName))
      {
         imprt = new ImportImpl(this).setName(strippedClassName);
         unit.imports().add(imprt.getInternal());
      }
      else if (hasImport(strippedClassName))
      {
         imprt = getImport(strippedClassName);
      }
      else
      {
         throw new IllegalArgumentException("Attempted to import the illegal type [" + strippedClassName + "]");
      }
      return imprt;
   }

   @Override
   public Import getImport(final String className)
   {
      List<Import> imports = getImports();
      for (Import imprt : imports)
      {
         if (imprt.getQualifiedName().equals(className) || imprt.getSimpleName().equals(className))
         {
            return imprt;
         }
      }
      return null;
   }

   @Override
   public Import getImport(final Class<?> type)
   {
      return getImport(type.getName());
   }

   @Override
   public <T extends ReadJavaSource<?>> Import getImport(final T type)
   {
      return getImport(type.getQualifiedName());
   }

   @Override
   public Import getImport(final ReadImport imprt)
   {
      return getImport(imprt.getQualifiedName());
   }

   @Override
   @SuppressWarnings("unchecked")
   public List<Import> getImports()
   {
      List<Import> results = new ArrayList<Import>();

      for (ImportDeclaration i : (List<ImportDeclaration>) unit.imports())
      {
         results.add(new ImportImpl(this, i));
      }

      return Collections.unmodifiableList(results);
   }

   @Override
   public boolean hasImport(final Class<?> type)
   {
      return hasImport(type.getName());
   }

   @Override
   public <T extends ReadJavaSource<T>> boolean hasImport(final T type)
   {
      return hasImport(type.getQualifiedName());
   }

   @Override
   public boolean hasImport(final ReadImport imprt)
   {
      return hasImport(imprt.getQualifiedName());
   }

   @Override
   public boolean hasImport(final String type)
   {
      String resultType = type;
      if (Types.isArray(type))
      {
         resultType = Types.stripArray(type);
      }
      if (Types.isGeneric(type))
      {
         resultType = Types.stripGenerics(type);
      }
      return getImport(resultType) != null;
   }

   @Override
   public boolean requiresImport(final Class<?> type)
   {
      return requiresImport(type.getName());
   }

   @Override
   public boolean requiresImport(final String type)
   {
      String resultType = type;
      if (Types.isArray(resultType))
      {
         resultType = Types.stripArray(type);
      }
      if (Types.isGeneric(resultType))
      {
         resultType = Types.stripGenerics(resultType);
      }
      if (!validImport(resultType)
               || hasImport(resultType)
               || Types.isJavaLang(resultType))
      {
         return false;
      }
      return true;
   }

   @Override
   public String resolveType(final String type)
   {
      String original = type;
      String result = type;

      // Strip away any characters that might hinder the type matching process
      if (Types.isArray(result))
      {
         original = Types.stripArray(result);
         result = Types.stripArray(result);
      }

      if (Types.isGeneric(result))
      {
         original = Types.stripGenerics(result);
         result = Types.stripGenerics(result);
      }

      if (Types.isPrimitive(result))
      {
         return result;
      }

      // Check for direct import matches first since they are the fastest and least work-intensive
      if (Types.isSimpleName(result))
      {
         if (!hasImport(type) && Types.isJavaLang(type))
         {
            result = "java.lang." + result;
         }

         if (result.equals(original))
         {
            for (ReadImport imprt : getImports())
            {
               if (Types.areEquivalent(result, imprt.getQualifiedName()))
               {
                  result = imprt.getQualifiedName();
                  break;
               }
            }
         }
      }

      // If we didn't match any imports directly, we might have a wild-card/on-demand import.
      if (Types.isSimpleName(result))
      {
         for (ReadImport imprt : getImports())
         {
            if (imprt.isWildcard())
            {
               // TODO warn if no wild-card resolvers are configured
               // TODO Test wild-card/on-demand import resolving
               for (WildcardImportResolver r : getImportResolvers())
               {
                  result = r.resolve(this, result);
                  if (Types.isQualified(result))
                     break;
               }
            }
         }
      }

      // No import matches and no wild-card/on-demand import matches means this class is in the same package.
      if (Types.isSimpleName(result))
      {
         if (getPackage() != null)
            result = getPackage() + "." + result;
      }

      return result;
   }

   private List<WildcardImportResolver> getImportResolvers()
   {
      if (resolvers == null)
      {
         resolvers = new ArrayList<WildcardImportResolver>();
         for (WildcardImportResolver r : resolvers)
         {
            resolvers.add(r);
         }
      }
      if (resolvers.size() == 0)
      {
         throw new IllegalStateException("No instances of [" + WildcardImportResolver.class.getName()
                  + "] were found on the classpath.");
      }
      return resolvers;
   }

   private boolean validImport(final String type)
   {
      return !Strings.isNullOrEmpty(type) && !Types.isPrimitive(type);
   }

   @Override
   public JavaPackageInfo removeImport(final String name)
   {
      for (ReadImport i : getImports())
      {
         if (i.getQualifiedName().equals(name))
         {
            removeImport(i);
            break;
         }
      }
      return this;
   }

   @Override
   public JavaPackageInfo removeImport(final Class<?> clazz)
   {
      return removeImport(clazz.getName());
   }

   @Override
   public <T extends ReadJavaSource<?>> JavaPackageInfo removeImport(final T type)
   {
      return removeImport(type.getQualifiedName());
   }

   @Override
   public JavaPackageInfo removeImport(final ReadImport imprt)
   {
      Object internal = imprt.getInternal();
      if (unit.imports().contains(internal))
      {
         unit.imports().remove(internal);
      }
      return this;
   }

   @Override
   public List<Member<JavaPackageInfo, ?>> getMembers()
   {
      return Collections.emptyList();
   }

   protected PackageDeclaration getPackageDeclaration()
   {
      if (pkg instanceof PackageDeclaration)
         return (PackageDeclaration) pkg;
      throw new ParserException("Source body was not of the expected type (PackageDeclaration).");
   }

   @Override
   public JavaPackageInfo setName(final String name)
   {
      throw new UnsupportedOperationException("Changing name of [" + getQualifiedName() + "] not supported.");
   }

   @Override
   public String getCanonicalName()
   {
      String result = getName();

      ReadJavaSource<?> enclosingType = this;
      while (enclosingType != enclosingType.getEnclosingType())
      {
         enclosingType = getEnclosingType();
         result = enclosingType.getEnclosingType().getName() + "." + result;
         enclosingType = enclosingType.getEnclosingType();
      }

      if (!Strings.isNullOrEmpty(getPackage()))
         result = getPackage() + "." + result;

      return result;
   }

   @Override
   public String getQualifiedName()
   {
      String result = getName();

      ReadJavaSource<?> enclosingType = this;
      while (enclosingType != enclosingType.getEnclosingType())
      {
         enclosingType = getEnclosingType();
         result = enclosingType.getEnclosingType().getName() + "$" + result;
         enclosingType = enclosingType.getEnclosingType();
      }

      if (!Strings.isNullOrEmpty(getPackage()))
         result = getPackage() + "." + result;

      return result;
   }

   /*
    * Package modifiers
    */
   @Override
   public String getPackage()
   {
      PackageDeclaration pkg = unit.getPackage();
      if (pkg != null)
      {
         return pkg.getName().getFullyQualifiedName();
      }
      else
      {
         return null;
      }
   }

   @Override
   public JavaPackageInfo setPackage(final String name)
   {
      if (unit.getPackage() == null)
      {
         unit.setPackage(unit.getAST().newPackageDeclaration());
      }
      unit.getPackage().setName(unit.getAST().newName(name));
      return this;
   }

   @Override
   public JavaPackageInfo setDefaultPackage()
   {
      unit.setPackage(null);
      return this;
   }

   @Override
   public boolean isDefaultPackage()
   {
      return unit.getPackage() == null;
   }

   /*
    * Visibility modifiers
    */
   @Override
   public boolean isPackagePrivate()
   {
      return (!isPublic() && !isPrivate() && !isProtected());
   }

   @Override
   public JavaPackageInfo setPackagePrivate()
   {
      modifiers.clearVisibility(getPackageDeclaration());
      return this;
   }

   @Override
   public boolean isPublic()
   {
      return modifiers.hasModifier(getPackageDeclaration(), ModifierKeyword.PUBLIC_KEYWORD);
   }

   @Override
   public JavaPackageInfo setPublic()
   {
      modifiers.clearVisibility(getPackageDeclaration());
      modifiers.addModifier(getPackageDeclaration(), ModifierKeyword.PUBLIC_KEYWORD);
      return this;
   }

   @Override
   public boolean isPrivate()
   {
      return modifiers.hasModifier(getPackageDeclaration(), ModifierKeyword.PRIVATE_KEYWORD);
   }

   @Override
   public JavaPackageInfo setPrivate()
   {
      modifiers.clearVisibility(getPackageDeclaration());
      modifiers.addModifier(getPackageDeclaration(), ModifierKeyword.PRIVATE_KEYWORD);
      return this;
   }

   @Override
   public boolean isProtected()
   {
      return modifiers.hasModifier(getPackageDeclaration(), ModifierKeyword.PROTECTED_KEYWORD);
   }

   @Override
   public JavaPackageInfo setProtected()
   {
      modifiers.clearVisibility(getPackageDeclaration());
      modifiers.addModifier(getPackageDeclaration(), ModifierKeyword.PROTECTED_KEYWORD);
      return this;
   }

   @Override
   public Visibility getVisibility()
   {
      return Visibility.getFrom(this);
   }

   @Override
   public JavaPackageInfo setVisibility(final Visibility scope)
   {
      return Visibility.set(this, scope);
   }

   /*
    * Non-manipulation methods.
    */
   /**
    * Return this {@link ReadJavaSource} file as a String
    */
   @Override
   public String toString()
   {
      Document document = new Document(this.document.get());

      try
      {
         TextEdit edit = unit.rewrite(document, null);
         edit.apply(document);
      }
      catch (Exception e)
      {
         throw new ParserException("Could not modify source: " + unit.toString(), e);
      }

      return Formatter.format(document.get());
   }

   @Override
   public Object getInternal()
   {
      return unit;
   }

   @Override
   public JavaPackageInfo getOrigin()
   {
      return this;
   }

   @Override
   public int hashCode()
   {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((pkg == null) ? 0 : pkg.hashCode());
      result = prime * result + ((document == null) ? 0 : document.hashCode());
      result = prime * result + ((enclosingType == null) ? 0 : enclosingType.hashCode());
      result = prime * result + ((unit == null) ? 0 : unit.hashCode());
      return result;
   }

   @Override
   public boolean equals(Object obj)
   {
      if (this == obj)
         return true;
      if (obj == null)
         return false;
      if (getClass() != obj.getClass())
         return false;
      JavaPackageInfoImpl other = (JavaPackageInfoImpl) obj;
      if (pkg == null)
      {
         if (other.pkg != null)
            return false;
      }
      else if (!pkg.equals(other.pkg))
         return false;
      if (document == null)
      {
         if (other.document != null)
            return false;
      }
      else if (!document.equals(other.document))
         return false;
      if (enclosingType == null)
      {
         if (other.enclosingType != null)
            return false;
      }
      else if (!enclosingType.equals(other.enclosingType))
         return false;
      if (unit == null)
      {
         if (other.unit != null)
            return false;
      }
      else if (!unit.equals(other.unit))
         return false;
      return true;
   }

   @Override
   public List<SyntaxError> getSyntaxErrors()
   {
      List<SyntaxError> result = new ArrayList<SyntaxError>();

      IProblem[] problems = unit.getProblems();
      if (problems != null)
      {
         for (IProblem problem : problems)
         {
            result.add(new SyntaxErrorImpl(this, problem));
         }
      }
      return result;
   }

   @Override
   public boolean hasSyntaxErrors()
   {
      return !getSyntaxErrors().isEmpty();
   }

   @Override
   public boolean isClass()
   {
      return false;
   }

   @Override
   public boolean isEnum()
   {
      return false;
   }

   @Override
   public boolean isInterface()
   {
      return false;
   }

   @Override
   public boolean isAnnotation()
   {
      return false;
   }

   @Override
   public List<JavaSource<?>> getNestedClasses()
   {
      List<AbstractTypeDeclaration> declarations = getNestedDeclarations(pkg);

      List<JavaSource<?>> result = new ArrayList<JavaSource<?>>();
      for (AbstractTypeDeclaration declaration : declarations)
      {
         result.add(JavaParserImpl.getJavaSource(this, document, unit, declaration));
      }
      return result;
   }

   private List<AbstractTypeDeclaration> getNestedDeclarations(ASTNode body)
   {

      TypeDeclarationFinderVisitor typeDeclarationFinder = new TypeDeclarationFinderVisitor();
      body.accept(typeDeclarationFinder);
      List<AbstractTypeDeclaration> declarations = typeDeclarationFinder.getTypeDeclarations();

      List<AbstractTypeDeclaration> result = new ArrayList<AbstractTypeDeclaration>(declarations);
      if (!declarations.isEmpty())
      {
         // We don't want to return the current class' declaration.
         result.remove(declarations.remove(0));
         for (AbstractTypeDeclaration declaration : declarations)
         {
            result.removeAll(getNestedDeclarations(declaration));
         }
      }

      return result;
   }

}
