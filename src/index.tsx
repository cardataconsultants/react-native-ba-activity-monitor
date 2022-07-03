import { NativeModules, Platform } from 'react-native';

const LINKING_ERROR =
  `The package 'react-native-ba-activity-monitor' doesn't seem to be linked. Make sure: \n\n` +
  Platform.select({ ios: "- You have run 'pod install'\n", default: '' }) +
  '- You rebuilt the app after installing the package\n' +
  '- You are not using Expo managed workflow\n';

const BaActivityMonitor = NativeModules.BaActivityMonitor
  ? NativeModules.BaActivityMonitor
  : new Proxy(
      {},
      {
        get() {
          throw new Error(LINKING_ERROR);
        },
      }
    );

export function start(): Promise<boolean> {
  return BaActivityMonitor.start();
}

export function stop(): Promise<boolean> {
  return BaActivityMonitor.start();
}

export default {
  start,
  stop,
};
