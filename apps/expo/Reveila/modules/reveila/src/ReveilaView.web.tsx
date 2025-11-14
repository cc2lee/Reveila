import * as React from 'react';

import { ReveilaViewProps } from './Reveila.types';

export default function ReveilaView(props: ReveilaViewProps) {
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
