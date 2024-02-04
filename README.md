# react-native-recording

Fork from [react-native-recording](https://github.com/qiuxiang/react-native-recording), but with fixed iOS build and
changed sound data response.

New sound response includes `timestamp` of the start of the sound:

```
{ data: number[]; timestamp: number }
```
