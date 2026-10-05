# sherpa-onnx (voce locale): il codice nativo legge classi e campi per nome.
-keep class com.k2fsa.sherpa.onnx.** { *; }
# commons-compress nomina formati che l'app non usa e le cui librerie non sono incluse.
-dontwarn org.apache.commons.compress.**
-dontwarn org.objectweb.asm.**
-dontwarn org.brotli.dec.**
-dontwarn org.tukaani.xz.**
-dontwarn com.github.luben.zstd.**
