#ifndef INFERENCE_H
#define INFERENCE_H

#include <string>
#include <vector>
#include <iostream>
#include <math.h>
#include "Network.h"
#include "BadInputException.h"
#include <fstream>
#include <cstdlib>
#include <ctime>
#include <algorithm>

using namespace std;

class Inference 
{
public:
	Inference(Network network);
	double MCMC_ask(string B, vector<string> e, vector<bool> vals, int iterations);
	double Markov_Blanket(int node);
private:
	Network bn;
	class BadInputException: public exception{ };
	vector<bool> x;
};

#endif
