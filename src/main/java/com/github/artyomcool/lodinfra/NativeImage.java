package com.github.artyomcool.lodinfra;

import com.oracle.svm.core.configure.ResourcesRegistry;
import com.oracle.svm.core.jni.JNIRuntimeAccess;
import com.sun.glass.ui.*;
import com.sun.javafx.PlatformUtil;
import com.sun.javafx.font.Glyph;
import com.sun.prism.GraphicsPipeline;
import impl.org.controlsfx.skin.SearchableComboBoxSkin;
import javafx.scene.control.Control;
import javafx.scene.image.Image;
import org.apache.commons.compress.archivers.zip.*;
import org.apache.poi.POIDocument;
import org.apache.poi.xslf.usermodel.XSLFComments;
import org.apache.xmlbeans.impl.store.Cursor;
import org.apache.xmlbeans.impl.store.Locale;
import org.apache.xmlbeans.impl.store.*;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.graalvm.nativeimage.impl.ConfigurationCondition;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.STCellType;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.impl.CalcChainDocumentImpl;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

public class NativeImage implements Feature {
    public void beforeAnalysis(Feature.BeforeAnalysisAccess access) {
        ResourcesRegistry resources = ImageSingletons.lookup(ResourcesRegistry.class);
        for (Class<?> c : Arrays.asList(POIDocument.class, XSLFComments.class, CalcChainDocumentImpl.class, SearchableComboBoxSkin.class)) {
            System.out.println("started " + c);
            try (JarInputStream jarFile = new JarInputStream(c.getProtectionDomain().getCodeSource().getLocation().openStream())) {
                while (true) {
                    JarEntry e = jarFile.getNextJarEntry();
                    if (e == null) {
                        break;
                    }
                    if (e.isDirectory()) {
                        continue;
                    }
                    String name = e.getName();
                    if (name.equals("module-info.class")) {
                        continue;
                    }
                    if (name.endsWith(".class")) {
                        registerConstruction(String.join(".", name.substring(0, name.length() - ".class".length()).split("/")));
                    } else {
                        resources.addResources(ConfigurationCondition.alwaysTrue(), name);
                    }
                }

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        for (Class<?> c : Arrays.asList(Control.class, javafx.stage.Window.class, Glyph.class)) {
            try (JarInputStream jarFile = new JarInputStream(c.getProtectionDomain().getCodeSource().getLocation().openStream())) {
                System.out.println("started " + c);
                while (true) {
                    JarEntry e = jarFile.getNextJarEntry();
                    if (e == null) {
                        break;
                    }
                    if (e.isDirectory()) {
                        continue;
                    }
                    String name = e.getName();
                    if (name.equals("module-info.class")) {
                        continue;
                    }
                    if (name.endsWith(".class")) {
                        String cl = String.join(".", name.substring(0, name.length() - ".class".length()).split("/"));
                        Class<?> a;
                        try {
                            a = Class.forName(cl, false, NativeImage.class.getClassLoader());
                        } catch (ClassNotFoundException ex) {
                            return;
                        }
                        if (cl.startsWith("com.sun.javafx.font")) {
                            registerAllNative(a);
                        }
                        if (cl.startsWith("com.sun.glass")) {
                            registerAllNative(a);
                        }
                        JNIRuntimeAccess.register(a);
                        RuntimeReflection.register(a);
                        RuntimeReflection.register(a.getDeclaredConstructors());
                        for (Method declaredMethod : a.getMethods()) {
                            switch (declaredMethod.getName()) {
                                case "getInstance":
                                case "getPipeline":
                                case "getFontFactory":
                                case "getFactory":
                                case "loadShader":
                                case "createRenderer":
                                    RuntimeReflection.register(declaredMethod);
                                    break;
                            }
                        }
                    } else {
                        resources.addResources(ConfigurationCondition.alwaysTrue(), name);
                    }
                }

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        System.out.println("continue");

        Arrays.asList(

                AsiExtraField.class,
                X5455_ExtendedTimestamp.class,
                X7875_NewUnix.class,
                JarMarker.class,
                UnicodePathExtraField.class,
                UnicodeCommentExtraField.class,
                Zip64ExtendedInformationExtraField.class,
                X000A_NTFS.class,
                X0014_X509Certificates.class,
                X0015_CertificateIdForFile.class,
                X0016_CertificateIdForCentralDirectory.class,
                X0017_StrongEncryptionHeader.class,
                X0019_EncryptionRecipientCertificateList.class,
                ResourceAlignmentExtraField.class
        ).forEach(a -> {
            RuntimeReflection.register(a);
            RuntimeReflection.register(a.getDeclaredConstructors());
        });

        for (Class<?> a : Gui.class.getDeclaredClasses()) {
            registerAll(a);
        }

        Arrays.asList(
                ByteBuffer.class,
                Runnable.class,
                ArrayList.class,
                Pixels.class,
                Screen.class,
                View.class,
                Window.class,
                Clipboard.class,
                com.sun.glass.ui.Cursor.class,
                Size.class,
                Map.class,
                HashSet.class,
                Set.class,
                Iterable.class,
                Iterator.class,
                Application.class,
                HashMap.class,
                String.class
        ).forEach(NativeImage::registerAllNative);

        Arrays.asList(
                "com.sun.glass.ui.gtk.GtkPixels",
                "com.sun.glass.ui.gtk.GtkView",
                "com.sun.glass.ui.gtk.GtkWindow",
                "com.sun.glass.ui.gtk.GtkCursor",
                "com.sun.glass.ui.gtk.GtkApplication",
                "com.sun.javafx.font.FontConfigManager$FontConfigFont",
                "com.sun.javafx.font.FontConfigManager$FcCompFont",
                "com.sun.prism.GraphicsPipeline",
                "com.sun.javafx.font.freetype.FT_GlyphSlotRec"
        ).forEach(NativeImage::registerAllNative);
    }

    private static void registerAll(Class<?> a) {
        RuntimeReflection.register(a);
        try {
            RuntimeReflection.register(a.getDeclaredConstructors());
        } catch (NoClassDefFoundError ignored) {
        }
        try {
            RuntimeReflection.register(a.getMethods());
        } catch (NoClassDefFoundError ignored) {
        }
        try {
            RuntimeReflection.register(a.getDeclaredMethods());
        } catch (NoClassDefFoundError ignored) {
        }
        try {
            RuntimeReflection.register(a.getDeclaredFields());
        } catch (NoClassDefFoundError ignored) {
        }
    }

    private static void registerConstruction(String name) {
        Class<?> a;
        try {
            a = Class.forName(name, false, NativeImage.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            return;
        }
        if (a.isInterface() || Modifier.isAbstract(a.getModifiers())) {
            return;
        }
        try {
            RuntimeReflection.register(a);
            RuntimeReflection.register(a.getConstructors());
        } catch (NoClassDefFoundError ignored) {
        }
    }

    private static void registerAll(String name) {
        Class<?> a;
        try {
            a = Class.forName(name, false, NativeImage.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            return;
        }
        registerAll(a);
    }

    private static void registerAllNative(String name) {
        Class<?> a;
        try {
            a = Class.forName(name, false, NativeImage.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            return;
        }
        registerAllNative(a);
    }

    private static void registerAllNative(Class<?> a) {
        JNIRuntimeAccess.register(a);
        JNIRuntimeAccess.register(a.getMethods());
        JNIRuntimeAccess.register(a.getDeclaredMethods());
        JNIRuntimeAccess.register(a.getDeclaredConstructors());
        JNIRuntimeAccess.register(a.getDeclaredFields());
        registerAll(a);
    }
}