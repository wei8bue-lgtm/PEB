export type ColorMode = {
  meta: Meta;
  sem: {
    schemes: {
      [background: string]: {
        background: Background;
        'special colors': SpecialColors;
        [scheme: string]: Scheme | Background | SpecialColors;
      };
    };
  };
};

export type Gradations = {
  [G in Colors]: Shades<G>;
} & {
  meta: Omit<Meta, 'colorMode' | 'name'>;
};

export interface Meta {
  version: string;
  colorMode: number;
  name: string;
}

export type Colors = 'yellow' | 'gray' | 'green' | 'turquoise' | 'blue' | 'purple' | 'red';

export type ShadeNumber =
  | '00'
  | '05'
  | '10'
  | '15'
  | '20'
  | '25'
  | '30'
  | '35'
  | '40'
  | '45'
  | '50'
  | '55'
  | '60'
  | '65'
  | '70'
  | '75'
  | '80'
  | '85'
  | '90'
  | '95'
  | '100';

export type ShadesKey<G extends Colors> = `${G} ${ShadeNumber}`;

export type Shades<G extends Colors> = {
  [S in ShadesKey<G>]: Color;
};

export type Color = {
  $type: string;
  $value: string;
  id: string;
  gradation: string;
  opacity: string;
  var: string;
};

export type Background = {
  $type: string;
  $value: string;
  id?: string;
  opacity: string;
  gradation?: string;
  var?: string;
};

export type SpecialColors = {
  [specialColor: string]: Color;
};

export type Types = 'major' | 'minor' | 'pure';

export type Role = {
  [role: string]: Color;
};

export type Scheme = {
  name: string;
  nested: boolean;
  major: Role;
  minor: Role;
  pure: Role;
};

declare const colorModes: ColorMode[];
export default colorModes;
