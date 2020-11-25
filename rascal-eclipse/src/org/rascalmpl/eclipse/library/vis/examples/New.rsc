@license{
  Copyright (c) 2009-2015 CWI
  All rights reserved. This program and the accompanying materials
  are made available under the terms of the Eclipse Public License v1.0
  which accompanies this distribution, and is available at
  http://www.eclipse.org/legal/epl-v10.html
}
module vis::examples::New

import util::Math;
import vis::Figure;
import vis::Render;

public void overlay1(){
	render(overlay([
		box(fillColor("red")),
		box(fillColor("green"),shrink(0.6)),
		box(fillColor("orange"),shrink(0.3))
	],shrink(0.9)));
}

public void overlay2(){
	render(overlay([
		box(fillColor("red")),
		box(fillColor("green"),shrink(0.6),vis::Figure::left()),
		box(fillColor("orange"),shrink(0.3),vis::Figure::right(),bottom())
	],shrink(0.9)));
}

public void shape1(){
	render(overlay([
		ellipse(shrink(0.05),fillColor("red"),align(x,y)) 
		| <x,y> <- [<0.0,0.0>,<0.0,1.0>,<1.0,1.0>,<1.0,0.0>]],shapeConnected(true),shapeClosed(true),fillColor("orange")
	));
}

	

public void path(int n){
	render(overlay([
		ellipse(shrink(0.02),fillColor("blue"),align((1.0/toReal(n)) * toReal(x) ,arbReal()))
		| x <- [0..n+1]],shapeConnected(true),shapeCurved(true),shapeClosed(true),fillColor("red")));
}




public void nominalKeyTest(){
	render(
		hcat(
				[box(fillColor(measure(s,"type"))) | 
				s <- ["Rascal","C++","Java"]] + [palleteKey("Types","type")])
				);
}



public void star(int n){
	piInc = PI() / toReal(n);
	angle = 0.0;
	
	coord = for(i <- [1..(2*n)+1]){
		radius = (i % 2 == 0) ? 0.5 : 0.2;
		append <sin(angle) * radius + 0.5 ,cos(angle) * radius + 0.5>;
		angle += piInc;
	}
	
	render(overlay([
		ellipse(shrink(0.02),fillColor("red"),align(x,y)) 
		| <x,y> <- coord],shapeConnected(true),shapeClosed(true),fillColor("orange")
	));
}

public void bubbles(int n){
	render(overlay([
		ellipse(
			hshrink(arbReal() * 0.4 + 0.1),
			vshrink(arbReal() * 0.4 + 0.1),
			fillColor(rrgba(arbReal(),arbReal(),arbReal(),arbReal())),
			align(arbReal(),arbReal())
		)
		| _ <- [1..n+1]]));
}

public void mondriaan(){
	// Painting by Piet Mondriaan: Composition II in Red, Blue, and Yellow, 1930
	render(grid([
			[
				vcat([box(),box()],hshrink(0.2),vshrink(0.8))
				,box(fillColor("red"),vshrink(0.8))
			],
	 		[
	 			box(fillColor("blue"),hshrink(0.2)),
	 			hcat([
	 				  box(hshrink(0.9)),
	 				  vcat([box(),box(fillColor("yellow"))])
					 ])
	 		]
		],std(lineWidth(6.0)),aspectRatio(1.0)));
} 


public void dutchFlag(){
	render(vcat([box(fillColor("red")),box(),box(fillColor("blue"))]));
}

public void frenchFlag(){
	render(hcat([box(fillColor("red")),box(),box(fillColor("blue"))]));
}

public void vennDiagram(){
	render(overlay([
		ellipse(text("A"),vis::Figure::left(),top(),shrink(0.6),fillColor(color("red",0.6))),
		ellipse(text("B"),vis::Figure::right(),top(),shrink(0.6),fillColor(color("green",0.6))),
		ellipse(text("C"),bottom(),shrink(0.6),fillColor(color("blue",0.6)))
		]));
}

public Figure tst0() = ellipse( ellipse(width(200), height(150), lineWidth(8), lineColor("green"), fillColor("yellow"))
      ,lineWidth(10), lineColor ("red"), fillColor("blue")
      ,std(resizable(false))
     );
    
public void tst() = render(tst0()); 
 
