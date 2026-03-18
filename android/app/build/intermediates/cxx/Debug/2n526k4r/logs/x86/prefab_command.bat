@echo off
"C:\\Program Files\\Android\\Android Studio\\jbr\\bin\\java" ^
  --class-path ^
  "C:\\Users\\raiha\\.gradle\\caches\\modules-2\\files-2.1\\com.google.prefab\\cli\\2.1.0\\aa32fec809c44fa531f01dcfb739b5b3304d3050\\cli-2.1.0-all.jar" ^
  com.google.prefab.cli.AppKt ^
  --build-system ^
  cmake ^
  --platform ^
  android ^
  --abi ^
  x86 ^
  --os-version ^
  24 ^
  --stl ^
  c++_shared ^
  --ndk-version ^
  28 ^
  --output ^
  "C:\\Users\\raiha\\AppData\\Local\\Temp\\agp-prefab-staging2388663158975402663\\staged-cli-output" ^
  "C:\\Users\\raiha\\.gradle\\caches\\8.13\\transforms\\b35113768ada5d4d686869392e080751\\transformed\\jetified-react-android-0.83.1-debug\\prefab" ^
  "C:\\Users\\raiha\\.gradle\\caches\\8.13\\transforms\\35d53287715db6b466239e4bd8c79d7c\\transformed\\jetified-hermes-android-0.14.0-debug\\prefab" ^
  "C:\\Users\\raiha\\.gradle\\caches\\8.13\\transforms\\53f078ae8f9296f85f11afa90a5e899b\\transformed\\jetified-fbjni-0.7.0\\prefab"
