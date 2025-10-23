import * as React from 'react';

import { ReveilaExpoModuleViewProps } from './ReveilaExpoModule.types';

export default function ReveilaExpoModuleView(props: ReveilaExpoModuleViewProps) {
  return (
    <div>
      <iframe
        style={{ flex: 1 }}
        src={props.url}
        onLoad={() => props.onLoad({ nativeEvent: { url: props.url } })}
      />
    </div>
  );
}
