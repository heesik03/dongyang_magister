import bookEmpty from '../assets/figma/44019.svg'
import bookBrand from '../assets/figma/f5cbb.svg'
import book from '../assets/feather/book-open.svg'
import grid from '../assets/feather/grid.svg'
import search from '../assets/feather/search.svg'
import file from '../assets/feather/file-text.svg'
import clock from '../assets/feather/clock.svg'
import check from '../assets/feather/check.svg'
import plus from '../assets/feather/plus.svg'
import arrow from '../assets/feather/arrow-right.svg'
import close from '../assets/feather/x.svg'
import settings from '../assets/feather/sliders.svg'
import save from '../assets/feather/save.svg'
import history from '../assets/feather/rotate-ccw.svg'
import edit from '../assets/feather/edit-2.svg'
import download from '../assets/feather/download.svg'
import trash from '../assets/feather/trash-2.svg'
import panelLeftClose from '../assets/feather/panel-left-close.svg'
import panelLeftOpen from '../assets/feather/panel-left-open.svg'
import logout from '../assets/feather/log-out.svg'
import barChart from '../assets/feather/bar-chart.svg'
import compare from '../assets/feather/compare.svg'
import chevron from '../assets/figma/117e3.svg'
export type IconName =
  | 'book-empty'
  | 'book'
  | 'grid'
  | 'plus'
  | 'arrow'
  | 'search'
  | 'file'
  | 'spark'
  | 'clock'
  | 'check'
  | 'logout'
  | 'menu'
  | 'close'
  | 'settings'
  | 'chevron'
  | 'save'
  | 'history'
  | 'eye'
  | 'eye-off'
  | 'edit'
  | 'refresh'
  | 'download'
  | 'trash'
  | 'gear'
  | 'cloud-check'
  | 'cloud-upload'
  | 'cloud-alert'
  | 'panel-left-close'
  | 'panel-left-open'
  | 'bar-chart'
  | 'compare'
const paths: Partial<Record<IconName, string>> = {
  eye: 'M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7Zm13 0a3 3 0 1 1-6 0 3 3 0 0 1 6 0Z',
  refresh: 'M20 7a9 9 0 0 0-15-1L2 9m0-6v6h6m-4 8a9 9 0 0 0 15 1l3-3m0 6v-6h-6',
  gear: 'M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8Zm8 4c0-.6-.1-1.2-.2-1.7l2-1.5-2-3.4-2.3.9a8 8 0 0 0-3-1.7L14 2h-4l-.5 2.6a8 8 0 0 0-3 1.7l-2.3-.9-2 3.4 2 1.5a8 8 0 0 0 0 3.4l-2 1.5 2 3.4 2.3-.9a8 8 0 0 0 3 1.7L10 22h4l.5-2.6a8 8 0 0 0 3-1.7l2.3.9 2-3.4-2-1.5c.1-.5.2-1.1.2-1.7Z',
  'eye-off':
    'M3 3l18 18M10.6 5.1A12 12 0 0 1 12 5c6.5 0 10 7 10 7a17 17 0 0 1-3.1 4M6.2 6.2A20 20 0 0 0 2 12s3.5 7 10 7a12 12 0 0 0 5.8-1.5M10 10a3 3 0 0 0 4 4',
  'cloud-check': 'M8 17H6a4 4 0 0 1-1-7.87A7 7 0 0 1 18.5 8 4.5 4.5 0 0 1 1 8.89M10 18l3 3 7-7',
  'cloud-upload':
    'M7 17H6a4 4 0 0 1-1-7.87A7 7 0 0 1 18.5 8 4.5 4.5 0 0 1 1 8.89M12 21V12m-3 3 3-3 3 3',
  'cloud-alert': 'M7 17H6a4 4 0 0 1-1-7.87A7 7 0 0 1 18.5 8 4.5 4.5 0 0 1 1 8.89M13 12v4m0 4v.01',
  'book-empty': '',
  chevron: 'm9 6 6 6-6 6',
  spark: 'm12 3 2.5 6.5L21 12l-6.5 2.5L12 21l-2.5-6.5L3 12l6.5-2.5L12 3Z',
  menu: 'M4 6h16M4 12h16M4 18h16',
}
const feather = {
  book,
  grid,
  search,
  file,
  clock,
  check,
  plus,
  arrow,
  close,
  settings,
  save,
  history,
  edit,
  download,
  trash,
  'panel-left-close': panelLeftClose,
  'panel-left-open': panelLeftOpen,
  logout,
  'bar-chart': barChart,
  compare,
}
export function Icon({ name, brand = false }: { name: IconName; brand?: boolean }) {
  const image =
    name === 'book' && brand
      ? bookBrand
      : name === 'book-empty'
        ? bookEmpty
        : name === 'chevron'
          ? chevron
          : undefined
  if (image) return <img className="design-icon" src={image} alt="" aria-hidden="true" />
  if (Object.hasOwn(feather, name)) {
    const asset = feather[name as keyof typeof feather]
    return (
      <span
        className="design-icon feather-icon"
        style={{ maskImage: 'url(' + asset + ')' }}
        aria-hidden="true"
      />
    )
  }
  return (
    <svg
      width="20"
      height="20"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d={paths[name]} />
    </svg>
  )
}
