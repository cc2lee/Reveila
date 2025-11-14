import { requireNativeView } from 'expo';
import * as React from 'react';

import { ReveilaViewProps } from './Reveila.types';

const NativeView: React.ComponentType<ReveilaViewProps> =
  requireNativeView('Reveila');

export default function ReveilaView(props: ReveilaViewProps) {
  return <NativeView {...props} />;
}
