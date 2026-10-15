import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';

import { AppRoot } from './AppRoot';
import './index.css';

const container = document.getElementById('root');
if (container === null) {
  throw new Error('마운트 지점 #root 가 문서에 없다');
}

createRoot(container).render(
  <StrictMode>
    <AppRoot />
  </StrictMode>,
);
