import { requireNativeComponent, ViewProps } from 'react-native';
import * as React from 'react';

export interface ReveilaViewProps extends ViewProps {
  url?: string;
  onLoad?: (event: { nativeEvent: { url: string } }) => void;
}

const NativeReveilaView = requireNativeComponent<ReveilaViewProps>('ReveilaView');

export default function ReveilaView(props: ReveilaViewProps) {
  return <NativeReveilaView {...props} />;
}
