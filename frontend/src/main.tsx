import React from 'react'
import ReactDOM from 'react-dom/client'
import { App } from './App'
import './style.css'

const Prototype = React.lazy(() => import('./prototype/Prototype').then(module => ({ default: module.Prototype })))

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>{new URLSearchParams(window.location.search).get('prototype') === '1' ? <React.Suspense fallback={<p role="status">Loading UI prototype…</p>}><Prototype /></React.Suspense> : <App />}</React.StrictMode>,
)
