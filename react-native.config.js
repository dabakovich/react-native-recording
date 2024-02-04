module.exports = {
  dependency: {
    platforms: {
      android: { sourceDir: "lib/android" }
    }
  },
  dependencies: {
    "react-native-recording": {
      root: __dirname,
      platforms: {
        android: {
          sourceDir: __dirname + "/lib/android",
          packageImportPath: "import cn.qiuxiang.react.recording.RecordingPackage;",
          packageInstance: "new RecordingPackage()"
        }
      }
    }
  }
};
