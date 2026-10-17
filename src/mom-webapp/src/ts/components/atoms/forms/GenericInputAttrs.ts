import m from 'mithril';

import { Form } from "./Form";
import { StatusMessageInterface } from '../../../utils/types';

export interface GenericInputAttrs<T> {
  params?:m.Attributes,
  refHolder: any,
  refProperty: string|number,
  requiredMessage?: string,
  format?: (s:T) => string,
  parse?: (s:string) => T,
  form?: Form,
  onchange?: ()=>void,
  id?:string,
  disabled?:boolean,
  status?:StatusMessageInterface
}
