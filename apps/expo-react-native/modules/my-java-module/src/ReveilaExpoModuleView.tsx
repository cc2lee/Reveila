import { requireNativeView } from 'expo';
import * as React from 'react';

import { ReveilaExpoModuleViewProps } from './ReveilaExpoModule.types';

const NativeView: React.ComponentType<ReveilaExpoModuleViewProps> =
  requireNativeView('ReveilaExpoModule');

export default function ReveilaExpoModuleView(props: ReveilaExpoModuleViewProps) {
  return <NativeView {...props} />;
}
